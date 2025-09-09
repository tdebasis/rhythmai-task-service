package com.rhythmai.task.service

import com.rhythmai.task.model.*
import com.rhythmai.task.repository.TaskRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import io.micrometer.core.annotation.Timed
import java.time.Instant

@Service
class TaskService(
    private val taskRepository: TaskRepository,
    private val analyticsService: AnalyticsService
) {
    
    @Timed(value = "task.create", description = "Time taken to create a task")
    fun createTask(
        userId: String, 
        request: CreateTaskRequest, 
        context: String? = null, 
        userEmail: String? = null,
        userTimezone: String = "UTC"
    ): TaskResponse {
        // Create DueBy object if date is provided
        val dueBy = request.dueBy?.let { dueByRequest ->
            // Validate ISO date format
            require(dueByRequest.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { 
                "Date must be ISO format YYYY-MM-DD" 
            }
            
            // If time specified but no date in original request, derive date from time
            val finalDate = if (dueByRequest.time != null && dueByRequest.date.isEmpty()) {
                val userZone = java.time.ZoneId.of(userTimezone)
                dueByRequest.time.atZone(userZone).toLocalDate().toString()
            } else {
                dueByRequest.date
            }
            
            DueBy(
                date = finalDate,
                time = dueByRequest.time,
                timeType = dueByRequest.timeType
            )
        }
        
        // Calculate position using enhanced logic
        val calculatedPosition = calculatePositionForCreate(userId, request)
        
        val task = Task(
            userId = userId,
            projectId = request.projectId,
            title = request.title,
            description = request.description,
            priority = request.priority,
            dueBy = dueBy,
            tags = request.tags,
            position = calculatedPosition
        )
        
        // Log task creation with user email
        val userInfo = userEmail?.let { " by user: $it" } ?: " by userId: $userId"
        val dueDateInfo = when {
            task.dueBy?.time != null -> "DateTime: ${task.dueBy.time}"
            task.dueBy?.date != null -> "Date: ${task.dueBy.date}"
            else -> "No due date"
        }
        println("🆕 TASK CREATED$userInfo - Title: '${task.title}', Priority: ${task.priority}, $dueDateInfo")
        
        val savedTask = taskRepository.save(task)
        
        // Privacy-first analytics tracking
        analyticsService.trackTaskEvent("task_created", mapOf(
            "priority" to task.priority.name,
            "has_due_date" to (task.dueBy != null),
            "is_time_specific" to (task.dueBy?.isTimeSpecific == true),
            "has_project" to (task.projectId != null),
            "has_description" to !task.description.isNullOrBlank(),
            "tag_count" to task.tags.size,
            "position_type" to when {
                request.position != null -> "manual"
                request.insertAtTop -> "top"
                request.insertAfterTaskId != null -> "after_task"
                else -> "auto_append"
            }
        ))
        
        // Track creation context if provided
        context?.let {
            analyticsService.trackWorkflowEvent("task_created_in_context", mapOf(
                "context" to it,
                "has_due_date" to (task.dueBy != null),
                "has_project" to (task.projectId != null)
            ))
            
            // Track inbox task creation specifically
            if (it == "inbox" && task.dueBy == null && task.projectId == null) {
                analyticsService.trackUserJourneyMilestone(userId, "inbox_task_created", mapOf(
                    "priority" to task.priority.name
                ))
            }
        }
        
        // User journey milestone tracking
        val totalTasks = taskRepository.countByUserId(userId)
        when (totalTasks) {
            1L -> analyticsService.trackUserJourneyMilestone(userId, "first_task", mapOf(
                "priority_chosen" to task.priority.name,
                "included_description" to !task.description.isNullOrBlank()
            ))
            5L -> analyticsService.trackUserJourneyMilestone(userId, "fifth_task", mapOf(
                "engagement_level" to "active"
            ))
            10L -> analyticsService.trackUserJourneyMilestone(userId, "power_user_threshold", mapOf(
                "user_type" to "power_user"
            ))
        }
        
        // Feature usage tracking
        if (request.dueBy != null) analyticsService.trackFeatureUsage(userId, "due_dates")
        if (request.tags.isNotEmpty()) analyticsService.trackFeatureUsage(userId, "tags", mapOf("tag_count" to request.tags.size))
        if (request.projectId != null) analyticsService.trackFeatureUsage(userId, "projects")
        if (request.position != null) analyticsService.trackFeatureUsage(userId, "manual_positioning")
        if (request.insertAtTop) analyticsService.trackFeatureUsage(userId, "priority_insertion")
        
        return TaskResponse.from(savedTask)
    }
    
    fun getTask(userId: String, taskId: String): TaskResponse? {
        val task = taskRepository.findByIdOrNull(taskId)
        return if (task != null && task.userId == userId) {
            TaskResponse.from(task)
        } else {
            null
        }
    }
    
    @Timed(value = "task.list", description = "Time taken to list tasks")
    fun getAllTasks(userId: String, pageable: Pageable): TaskListResponse {
        val page = taskRepository.findByUserIdOrderByPositionAsc(userId, pageable)
        return TaskListResponse(
            tasks = page.content.map { TaskResponse.from(it) },
            total = page.totalElements,
            page = pageable.pageNumber,
            size = pageable.pageSize
        )
    }
    
    fun getTasksByCompleted(userId: String, completed: Boolean, pageable: Pageable): TaskListResponse {
        val page = taskRepository.findByUserIdAndCompletedOrderByPositionAsc(userId, completed, pageable)
        
        // Track filter usage
        analyticsService.trackWorkflowEvent("filter_applied", mapOf(
            "filter_type" to "completion_status",
            "filter_value" to completed,
            "results_count" to page.totalElements,
            "page_requested" to pageable.pageNumber
        ))
        
        return TaskListResponse(
            tasks = page.content.map { TaskResponse.from(it) },
            total = page.totalElements,
            page = pageable.pageNumber,
            size = pageable.pageSize
        )
    }
    
    fun getTasksByPriority(userId: String, priority: Priority, completed: Boolean = false): List<TaskResponse> {
        val tasks = taskRepository.findByUserIdAndPriorityAndCompletedOrderByPositionAsc(userId, priority, completed)
        
        // Track priority filter usage
        analyticsService.trackWorkflowEvent("filter_applied", mapOf(
            "filter_type" to "priority",
            "filter_value" to priority.name,
            "results_count" to tasks.size
        ))
        
        return tasks.map { TaskResponse.from(it) }
    }
    
    fun getUpcomingTasks(userId: String, start: Instant, end: Instant): List<TaskResponse> {
        // TODO: Update this method to use hybrid date model
        val tasks = emptyList<Task>() // taskRepository.findByUserIdAndDueDateBetweenOrderByDueDateAsc(userId, start, end)
        
        // Track upcoming tasks view
        analyticsService.trackWorkflowEvent("upcoming_tasks_viewed", mapOf(
            "date_range_days" to java.time.temporal.ChronoUnit.DAYS.between(start, end),
            "results_count" to tasks.size,
            "has_upcoming_tasks" to tasks.isNotEmpty()
        ))
        
        return tasks.map { TaskResponse.from(it) }
    }
    
    fun getOverdueTasks(userId: String): List<TaskResponse> {
        val now = Instant.now()
        // TODO: Update this method to use hybrid date model
        val tasks = emptyList<Task>() // taskRepository.findByUserIdAndDueDateLessThanEqualAndCompletedFalseOrderByDueDateAsc(userId, now)
        
        // Track overdue tasks view - important metric for user engagement
        analyticsService.trackWorkflowEvent("overdue_tasks_viewed", mapOf(
            "overdue_count" to tasks.size,
            "has_overdue_tasks" to tasks.isNotEmpty(),
            "urgency_level" to when {
                tasks.isEmpty() -> "none"
                tasks.size <= 3 -> "low"
                tasks.size <= 10 -> "medium"
                else -> "high"
            }
        ))
        
        return tasks.map { TaskResponse.from(it) }
    }
    
    fun getTasksByTag(userId: String, tag: String, completed: Boolean = false): List<TaskResponse> {
        val tasks = taskRepository.findByUserIdAndTagsContainingAndCompletedOrderByPositionAsc(userId, tag, completed)
        
        // Track tag-based filtering
        analyticsService.trackWorkflowEvent("filter_applied", mapOf(
            "filter_type" to "tag",
            "filter_value" to tag,
            "results_count" to tasks.size,
            "tag_popularity" to when {
                tasks.isEmpty() -> "unused"
                tasks.size <= 2 -> "rare"
                tasks.size <= 10 -> "common"
                else -> "popular"
            }
        ))
        
        return tasks.map { TaskResponse.from(it) }
    }
    
    @Timed(value = "task.search", description = "Time taken to search tasks")
    fun searchTasks(userId: String, searchText: String, completed: Boolean = false): List<TaskResponse> {
        val tasks = taskRepository.searchByUserIdAndTextAndCompleted(userId, searchText, completed)
        
        // Track search activity
        analyticsService.trackWorkflowEvent("search_performed", mapOf(
            "search_length" to searchText.length,
            "results_count" to tasks.size,
            "has_results" to tasks.isNotEmpty()
        ))
        
        return tasks.map { TaskResponse.from(it) }
    }
    
    @Timed(value = "task.update", description = "Time taken to update a task")
    fun updateTask(
        userId: String, 
        taskId: String, 
        request: UpdateTaskRequest, 
        userEmail: String? = null,
        userTimezone: String = "UTC"
    ): TaskResponse? {
        val existingTask = taskRepository.findByIdOrNull(taskId)
            ?: return null
            
        if (existingTask.userId != userId) {
            return null
        }
        
        // Handle DueBy updates
        val updatedDueBy = when {
            request.clearDueDate -> null
            request.dueBy != null -> {
                // Validate ISO date format
                require(request.dueBy.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { 
                    "Date must be ISO format YYYY-MM-DD" 
                }
                
                // Create new DueBy from request
                DueBy(
                    date = request.dueBy.date,
                    time = request.dueBy.time,
                    timeType = request.dueBy.timeType
                )
            }
            else -> existingTask.dueBy
        }
        
        // Calculate position using enhanced logic
        val calculatedPosition = calculatePositionForUpdate(userId, existingTask, request)
        
        // Create CompletedOn if marking as complete
        val completedOn = if (request.completed == true && existingTask.completed != true) {
            CompletedOn.now(userTimezone)
        } else if (request.completed == false && existingTask.completed == true) {
            null // Clearing completion
        } else {
            existingTask.completedOn
        }
        
        val updatedTask = existingTask.copy(
            title = request.title ?: existingTask.title,
            description = request.description ?: existingTask.description,
            projectId = request.projectId ?: existingTask.projectId,
            completed = request.completed ?: existingTask.completed,
            priority = request.priority ?: existingTask.priority,
            dueBy = updatedDueBy,
            tags = request.tags ?: existingTask.tags,
            position = calculatedPosition,
            updatedAt = Instant.now(),
            completedOn = completedOn
        )
        
        // Log task update with user email
        val userInfo = userEmail?.let { " by user: $it" } ?: " by userId: $userId"
        val completionChange = if (request.completed == true && existingTask.completed != true) {
            " (COMPLETED)"
        } else if (request.completed == false && existingTask.completed == true) {
            " (MARKED INCOMPLETE)"
        } else ""
        println("✏️ TASK UPDATED$userInfo$completionChange - Title: '${updatedTask.title}', ID: $taskId")
        
        val savedTask = taskRepository.save(updatedTask)
        
        // Track task update events
        val updateProperties = mutableMapOf<String, Any>(
            "fields_changed" to mutableListOf<String>()
        )
        
        // Track what changed
        val changedFields = mutableListOf<String>()
        if (request.title != null && request.title != existingTask.title) changedFields.add("title")
        if (request.description != null && request.description != existingTask.description) changedFields.add("description")
        if (request.completed != null && request.completed != existingTask.completed) {
            changedFields.add("completed")
            updateProperties["completion_status"] = request.completed
        }
        if (request.priority != null && request.priority != existingTask.priority) {
            changedFields.add("priority")
            updateProperties["new_priority"] = request.priority.name
        }
        val requestHasDue = request.dueBy != null
        val existingHasDue = existingTask.dueBy != null
        if (requestHasDue != existingHasDue || 
            (requestHasDue && existingHasDue)) {
            changedFields.add("due_date")
            updateProperties["date_change_type"] = when {
                !existingHasDue -> "added"
                !requestHasDue -> "removed"
                else -> "modified"
            }
        }
        if (request.tags != null && request.tags != existingTask.tags) changedFields.add("tags")
        if (calculatedPosition != existingTask.position) changedFields.add("position")
        
        updateProperties["fields_changed"] = changedFields
        updateProperties["change_count"] = changedFields.size
        
        analyticsService.trackTaskEvent("task_updated", updateProperties)
        
        // Track completion milestones for engagement analysis
        if (request.completed == true && existingTask.completed != true) {
            val completedCount = taskRepository.countByUserIdAndCompleted(userId, true)
            when (completedCount) {
                1L -> analyticsService.trackUserJourneyMilestone(userId, "first_completion", mapOf(
                    "time_to_first_completion" to java.time.Duration.between(existingTask.createdAt, Instant.now()).toDays()
                ))
                5L -> analyticsService.trackUserJourneyMilestone(userId, "fifth_completion", mapOf(
                    "completion_velocity" to "active"
                ))
                25L -> analyticsService.trackUserJourneyMilestone(userId, "productive_user", mapOf(
                    "user_type" to "productive"
                ))
            }
            
            // Track engagement drivers
            val todayCompletions = countTodayCompletions(userId)
            if (todayCompletions >= 3) {
                analyticsService.trackUserJourneyMilestone(userId, "productive_day", mapOf(
                    "daily_completions" to todayCompletions
                ))
            }
        }
        
        return TaskResponse.from(savedTask)
    }
    
    fun deleteTask(userId: String, taskId: String): Boolean {
        val task = taskRepository.findByIdOrNull(taskId)
            ?: return false
            
        if (task.userId != userId) {
            return false
        }
        
        // Track deletion before removing
        analyticsService.trackTaskEvent("task_deleted", mapOf(
            "was_completed" to task.completed,
            "had_due_date" to (task.dueBy != null),
            "had_project" to (task.projectId != null),
            "tag_count" to task.tags.size,
            "priority" to task.priority.name
        ))
        
        taskRepository.deleteById(taskId)
        return true
    }
    
    fun getTaskStats(userId: String): TaskStats {
        val total = taskRepository.countByUserId(userId)
        val completed = taskRepository.countByUserIdAndCompleted(userId, true)
        val pending = total - completed
        
        // Track stats access - indicates user engagement with productivity metrics
        analyticsService.trackWorkflowEvent("task_stats_viewed", mapOf(
            "total_tasks" to total,
            "completed_tasks" to completed,
            "pending_tasks" to pending,
            "completion_rate" to if (total > 0) (completed.toDouble() / total * 100).toInt() else 0,
            "productivity_level" to when {
                total == 0L -> "new_user"
                completed.toDouble() / total >= 0.8 -> "high_achiever"
                completed.toDouble() / total >= 0.5 -> "steady_progress"
                completed.toDouble() / total >= 0.2 -> "getting_started"
                else -> "needs_support"
            }
        ))
        
        return TaskStats(
            total = total,
            completed = completed,
            pending = pending
        )
    }

    /**
     * Move a task to a new position using various strategies
     */
    fun moveTask(
        userId: String,
        taskId: String,
        moveRequest: MoveTaskRequest,
        userEmail: String? = null
    ): TaskResponse? {
        val task = taskRepository.findByIdOrNull(taskId) ?: return null
        
        // Verify ownership
        if (task.userId != userId) {
            return null
        }
        
        // Auto-detect overdue context based on task states
        val todayDateStr = java.time.LocalDate.now().toString()
        val taskIsOverdue = task.isOverdue(todayDateStr)
        
        // Check if reference task is also overdue (we'll check this later for each case)
        // For now, just check if explicit context is set
        var isOverdueContext = moveRequest.context == "overdue"
        
        // We'll enhance this detection when we actually have the reference task
        // This allows us to auto-detect without requiring explicit context
        
        // No date changes allowed - only position changes within same date context
        val currentContext = getDateContext(task.dueBy)
        
        // Track the final decision on whether to use overdue context
        var finalIsOverdueContext = isOverdueContext
        
        // Calculate new position based on move strategy
        val newPosition = when {
            moveRequest.insertAfter != null -> {
                val afterTask = taskRepository.findByIdOrNull(moveRequest.insertAfter) 
                    ?: throw TaskNotFoundException("Reference task not found: ${moveRequest.insertAfter}")
                if (afterTask.userId != userId) {
                    throw UnauthorizedException("Reference task belongs to different user")
                }
                
                // Auto-detect if both tasks are overdue
                val afterTaskIsOverdue = afterTask.isOverdue(todayDateStr)
                val shouldUseOverdueContext = isOverdueContext || (taskIsOverdue && afterTaskIsOverdue)
                
                // Debug logging
                println("🔍 AUTO-DETECT: task=${task.title} overdue=$taskIsOverdue, afterTask=${afterTask.title} overdue=$afterTaskIsOverdue, shouldUse=$shouldUseOverdueContext")
                
                // Use overdue context if auto-detected or explicitly set
                if (shouldUseOverdueContext) {
                    // Verify both tasks are actually overdue
                    if (!taskIsOverdue || !afterTaskIsOverdue) {
                        throw IllegalArgumentException("Both tasks must be overdue for overdue context reordering")
                    }
                    finalIsOverdueContext = true  // Update the final decision
                    // For overdue context, calculate based on overduePosition
                    calculateOverduePositionAfter(afterTask, userId)
                } else {
                    // Regular date context validation
                    if (getDateContext(afterTask.dueBy) != currentContext) {
                        throw IllegalArgumentException("Reference task is not in the same date context")
                    }
                    calculatePositionAfterTask(afterTask, currentContext, userId)
                }
            }
            
            moveRequest.insertBefore != null -> {
                val beforeTask = taskRepository.findByIdOrNull(moveRequest.insertBefore) 
                    ?: throw TaskNotFoundException("Reference task not found: ${moveRequest.insertBefore}")
                if (beforeTask.userId != userId) {
                    throw UnauthorizedException("Reference task belongs to different user")
                }
                
                // Auto-detect if both tasks are overdue
                val beforeTaskIsOverdue = beforeTask.isOverdue(todayDateStr)
                val shouldUseOverdueContext = isOverdueContext || (taskIsOverdue && beforeTaskIsOverdue)
                
                // Use overdue context if auto-detected or explicitly set
                if (shouldUseOverdueContext) {
                    // Verify both tasks are actually overdue
                    if (!taskIsOverdue || !beforeTaskIsOverdue) {
                        throw IllegalArgumentException("Both tasks must be overdue for overdue context reordering")
                    }
                    finalIsOverdueContext = true  // Update the final decision
                    // For overdue context, calculate based on overduePosition
                    calculateOverduePositionBefore(beforeTask, userId)
                } else {
                    // Regular date context validation
                    if (getDateContext(beforeTask.dueBy) != currentContext) {
                        throw IllegalArgumentException("Reference task is not in the same date context")
                    }
                    calculatePositionBeforeTask(beforeTask, currentContext, userId)
                }
            }
            
            moveRequest.moveToTop -> {
                finalIsOverdueContext = isOverdueContext || taskIsOverdue
                if (finalIsOverdueContext) {
                    // For overdue context, find minimum overduePosition
                    getMinOverduePosition(userId) - 1000
                } else {
                    getMinPositionForDateContext(userId, currentContext) - 1000
                }
            }
            
            moveRequest.moveToBottom -> {
                finalIsOverdueContext = isOverdueContext || taskIsOverdue
                if (finalIsOverdueContext) {
                    // For overdue context, find maximum overduePosition
                    getMaxOverduePosition(userId) + 1000
                } else {
                    getMaxPositionForDateContext(userId, currentContext) + 1000
                }
            }
            
            else -> task.position // No change
        }
        
        // Update task with new position only (no date changes)
        val updatedTask = if (finalIsOverdueContext) {
            // For overdue context, update overduePosition instead of position
            task.copy(
                overduePosition = newPosition,
                updatedAt = Instant.now()
            )
        } else {
            // For regular moves, update position only
            task.copy(
                position = newPosition,
                updatedAt = Instant.now()
            )
        }
        
        val savedTask = taskRepository.save(updatedTask)
        
        // Log the move
        val moveDescription = when {
            finalIsOverdueContext -> "reordered in overdue context"
            moveRequest.insertAfter != null -> "reordered after another task"
            moveRequest.insertBefore != null -> "reordered before another task"
            moveRequest.moveToTop -> "moved to top"
            moveRequest.moveToBottom -> "moved to bottom"
            else -> "reordered in ${currentContext}"
        }
        println("📍 TASK ${moveDescription.uppercase()}${userEmail?.let { " by $it" } ?: ""} - '${task.title}' at position $newPosition")
        
        // Track analytics
        analyticsService.trackWorkflowEvent("task_moved", mapOf(
            "move_strategy" to when {
                moveRequest.insertAfter != null -> "insert_after"
                moveRequest.insertBefore != null -> "insert_before"
                moveRequest.moveToTop -> "move_to_top"
                moveRequest.moveToBottom -> "move_to_bottom"
                else -> "position_only"
            },
            "is_overdue_context" to finalIsOverdueContext,
            "context" to if (finalIsOverdueContext) "overdue" else currentContext,
            "new_position" to newPosition
        ))
        
        return TaskResponse.from(savedTask)
    }
    
    private fun calculatePositionAfterTask(afterTask: Task, context: String, userId: String): Int {
        // Find the next task after the reference task
        val nextTask = findNextTaskInContext(userId, context, afterTask.position)
        
        return if (nextTask != null) {
            val midpoint = (afterTask.position + nextTask.position) / 2
            if (midpoint - afterTask.position < 10) {
                // Gap too small, append to end for now
                getMaxPositionForDateContext(userId, context) + 1000
            } else {
                midpoint
            }
        } else {
            // No next task, place after with standard gap
            afterTask.position + 1000
        }
    }
    
    private fun calculatePositionBeforeTask(beforeTask: Task, context: String, userId: String): Int {
        // Find the task before the reference task
        val previousTask = findPreviousTaskInContext(userId, context, beforeTask.position)
        
        return if (previousTask != null) {
            val midpoint = (previousTask.position + beforeTask.position) / 2
            if (beforeTask.position - midpoint < 10) {
                // Gap too small, place at top for now
                getMinPositionForDateContext(userId, context) - 1000
            } else {
                midpoint
            }
        } else {
            // No previous task, place before with standard gap
            beforeTask.position - 1000
        }
    }
    
    private fun findPreviousTaskInContext(userId: String, context: String, beforePosition: Int): Task? {
        return when {
            context.startsWith("date:") -> {
                val dateStr = context.substringAfter("date:")
                taskRepository.findByUserIdAndDueByDateOrderByPositionAsc(userId, dateStr)
                    .lastOrNull { it.position < beforePosition }
            }
            context == "inbox" -> {
                taskRepository.findByUserIdAndDueByIsNullOrderByPositionAsc(userId)
                    .lastOrNull { it.position < beforePosition }
            }
            else -> null
        }
    }
    
    // Enhanced position calculation logic
    private fun calculatePositionForCreate(userId: String, request: CreateTaskRequest): Int {
        return when {
            // 1. Frontend specified exact position
            request.position != null -> request.position
            
            // 2. Contextual insertion after specific task
            request.insertAfterTaskId != null -> calculatePositionAfter(request.insertAfterTaskId)
            
            // 3. Insert at top of context
            request.insertAtTop -> {
                val context = getDateContext(request.dueBy?.let { DueBy(it.date, it.time, it.timeType) })
                getMinPositionForDateContext(userId, context) - 1000
            }
            
            // 4. Default: append to end of appropriate context
            else -> {
                val context = getDateContext(request.dueBy?.let { DueBy(it.date, it.time, it.timeType) })
                getMaxPositionForDateContext(userId, context) + 1000
            }
        }
    }
    
    private fun calculatePositionForUpdate(userId: String, existingTask: Task, request: UpdateTaskRequest): Int {
        return when {
            // 1. Frontend specified exact position
            request.position != null -> request.position
            
            // 2. Contextual insertion
            request.insertAfterTaskId != null -> calculatePositionAfter(request.insertAfterTaskId)
            
            // 3. Date change - append to new date context
            request.dueBy != null && (existingTask.dueBy == null || request.dueBy.date != existingTask.dueBy.date) -> {
                val newContext = getDateContext(DueBy(request.dueBy.date, request.dueBy.time, request.dueBy.timeType))
                getMaxPositionForDateContext(userId, newContext) + 1000
            }
            
            // 4. Same date, no position change - keep existing
            else -> existingTask.position
        }
    }
    
    private fun calculatePositionAfter(afterTaskId: String): Int {
        val afterTask = taskRepository.findByIdOrNull(afterTaskId) ?: return 1000
        // Find the next task in the same context
        val context = getDateContext(afterTask.dueBy)
        val nextTask = findNextTaskInContext(afterTask.userId, context, afterTask.position)
        
        // Calculate midpoint, ensuring minimum gap of 10
        return if (nextTask != null) {
            val midpoint = (afterTask.position + nextTask.position) / 2
            if (midpoint - afterTask.position < 10) {
                // Gap too small, need rebalancing - for now, append to end
                getMaxPositionForDateContext(afterTask.userId, context) + 1000
            } else {
                midpoint
            }
        } else {
            // No next task, append after this one
            afterTask.position + 1000
        }
    }
    
    private fun getDateContext(dueBy: DueBy?): String {
        return if (dueBy != null) {
            "date:${dueBy.date}"
        } else {
            "inbox"
        }
    }
    
    private fun getMaxPositionForDateContext(userId: String, context: String): Int {
        return when {
            context.startsWith("date:") -> {
                val dateStr = context.substringAfter("date:")
                // Use repository method to find tasks for this date
                taskRepository.findByUserIdAndDueByDateOrderByPositionAsc(userId, dateStr)
                    .lastOrNull()?.position ?: 0
            }
            context == "inbox" -> {
                taskRepository.findByUserIdAndDueByIsNullOrderByPositionAsc(userId)
                    .lastOrNull()?.position ?: 0
            }
            else -> 0
        }
    }
    
    private fun getMinPositionForDateContext(userId: String, context: String): Int {
        return when {
            context.startsWith("date:") -> {
                val dateStr = context.substringAfter("date:")
                // Use repository method to find tasks for this date
                taskRepository.findByUserIdAndDueByDateOrderByPositionAsc(userId, dateStr)
                    .firstOrNull()?.position ?: 1000
            }
            context == "inbox" -> {
                taskRepository.findByUserIdAndDueByIsNullOrderByPositionAsc(userId)
                    .firstOrNull()?.position ?: 1000
            }
            else -> 1000
        }
    }
    
    // View-based filtering methods
    @Timed(value = "task.inbox", description = "Time taken to get inbox tasks")
    fun getInboxTasks(
        userId: String, 
        completed: Boolean, 
        pageable: Pageable,
        userTimezone: String = "UTC"
    ): TaskListResponse {
        // Calculate today's date in user timezone
        val userZone = java.time.ZoneId.of(userTimezone)
        val todayDateStr = java.time.LocalDate.now(userZone).toString()
        
        // Inbox: tasks without due date AND without project, plus today's completed tasks
        val page = taskRepository.findInboxTasksIncludingTodayCompleted(
            userId, completed, todayDateStr, pageable
        )
        
        // Track inbox view usage
        analyticsService.trackWorkflowEvent("inbox_viewed", mapOf(
            "task_count" to page.totalElements,
            "completed_filter" to completed,
            "has_inbox_tasks" to page.hasContent(),
            "includes_today_completed" to true
        ))
        
        // Track inbox zero achievement (only for unorganized incomplete tasks)
        val unorganizedCount = taskRepository.findByUserIdAndDueByIsNullAndProjectIdIsNullAndCompletedOrderByPositionAsc(
            userId, false, pageable
        ).totalElements
        
        if (unorganizedCount == 0L) {
            analyticsService.trackUserJourneyMilestone(userId, "inbox_zero_achieved", mapOf(
                "timestamp" to Instant.now()
            ))
        }
        
        return TaskListResponse(
            tasks = page.content.map { TaskResponse.from(it) },
            total = page.totalElements,
            page = pageable.pageNumber,
            size = pageable.pageSize
        )
    }
    
    @Timed(value = "task.today", description = "Time taken to get today's tasks")
    fun getTodayTasks(
        userId: String, 
        completed: Boolean, 
        pageable: Pageable,
        userTimezone: String = "UTC"
    ): TaskListResponse {
        // Calculate "today" in user's timezone
        val userZone = java.time.ZoneId.of(userTimezone)
        val now = java.time.ZonedDateTime.now(userZone)
        val todayDateStr = now.toLocalDate().toString()  // "2025-09-06" in user's timezone
        val todayStart = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS).toInstant()
        val todayEnd = now.plusDays(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS).toInstant()
        
        // For today view: return ALL tasks due today (completed or not) + incomplete overdue
        // This lets the UI decide how to display completed vs incomplete tasks
        var page = taskRepository.findAllTodayViewTasks(userId, todayDateStr, todayStart, todayEnd, pageable)
        
        // Assign positions to newly overdue tasks (incremental positioning)
        assignOverduePositions(page.content, todayDateStr)
        
        // Re-fetch the tasks to get updated overduePosition values
        // This is necessary because assignOverduePositions saves to DB but doesn't update the in-memory list
        page = taskRepository.findAllTodayViewTasks(userId, todayDateStr, todayStart, todayEnd, pageable)
        
        // Sort tasks: overdue tasks by overduePosition, today's tasks by position
        val sortedTasks = page.content.sortedWith { task1, task2 ->
            val isOverdue1 = task1.isOverdue(todayDateStr)
            val isOverdue2 = task2.isOverdue(todayDateStr)
            
            when {
                // Both are overdue: sort by overduePosition
                isOverdue1 && isOverdue2 -> {
                    val pos1 = task1.overduePosition ?: Int.MAX_VALUE
                    val pos2 = task2.overduePosition ?: Int.MAX_VALUE
                    println("🔍 SORTING OVERDUE: '${task1.title}' (pos=$pos1) vs '${task2.title}' (pos=$pos2) -> ${pos1.compareTo(pos2)}")
                    pos1.compareTo(pos2)
                }
                // Both are today's tasks: sort by position
                !isOverdue1 && !isOverdue2 -> task1.position.compareTo(task2.position)
                // Mixed: overdue tasks come first
                isOverdue1 -> -1
                else -> 1
            }
        }
        
        // Track today view usage
        analyticsService.trackWorkflowEvent("today_view", mapOf(
            "task_count" to page.totalElements,
            "completed_filter" to completed,
            "has_tasks_today" to page.hasContent()
        ))
        
        // Check for productive day milestone
        val completedToday = taskRepository.countByUserIdAndCompletedTrueAndCompletedOnDateEquals(
            userId, todayDateStr
        )
        if (completedToday >= 5) {
            analyticsService.trackUserJourneyMilestone(userId, "productive_day", mapOf(
                "tasks_completed" to completedToday
            ))
        }
        
        return TaskListResponse(
            tasks = sortedTasks.map { TaskResponse.from(it) },
            total = page.totalElements,
            page = pageable.pageNumber,
            size = pageable.pageSize
        )
    }
    
    @Timed(value = "task.upcoming", description = "Time taken to get upcoming tasks")
    fun getUpcomingTasks(
        userId: String, 
        completed: Boolean, 
        pageable: Pageable,
        userTimezone: String = "UTC"
    ): TaskListResponse {
        // Calculate "tomorrow" in user's timezone
        val userZone = java.time.ZoneId.of(userTimezone)
        val now = java.time.ZonedDateTime.now(userZone)
        val todayDateStr = now.toLocalDate().toString()
        val tomorrow = now.plusDays(1)
        val tomorrowStart = tomorrow.truncatedTo(java.time.temporal.ChronoUnit.DAYS).toInstant()
        
        // Future tasks (tomorrow onwards) - hybrid query
        val page = taskRepository.findUpcomingTasks(
            userId, todayDateStr, tomorrowStart, completed, pageable
        )
        
        // Track upcoming view usage
        analyticsService.trackWorkflowEvent("upcoming_view", mapOf(
            "task_count" to page.totalElements,
            "completed_filter" to completed,
            "has_upcoming_tasks" to page.hasContent()
        ))
        
        return TaskListResponse(
            tasks = page.content.map { TaskResponse.from(it) },
            total = page.totalElements,
            page = pageable.pageNumber,
            size = pageable.pageSize
        )
    }
    
    fun trackViewContext(userId: String, view: String) {
        // Track which views users are accessing
        analyticsService.trackFeatureUsage(userId, "view_navigation", mapOf(
            "view" to view,
            "timestamp" to Instant.now()
        ))
    }
    
    /**
     * Assign positions to newly overdue tasks (incremental positioning)
     * Only tasks that are overdue and don't have an overduePosition get one
     */
    private fun assignOverduePositions(tasks: List<Task>, todayDateStr: String) {
        // Separate overdue tasks into positioned and unpositioned
        val overdueTasks = tasks.filter { it.isOverdue(todayDateStr) }
        val (positioned, unpositioned) = overdueTasks.partition { it.overduePosition != null }
        
        if (unpositioned.isNotEmpty()) {
            // Find the maximum position among already positioned overdue tasks
            val maxPosition = positioned.maxOfOrNull { it.overduePosition!! } ?: 0
            
            // Assign positions to newly overdue tasks, appending to the end
            // Group by priority for better organization
            val sortedUnpositioned = unpositioned.sortedWith(
                compareBy(
                    { -it.priority.ordinal },  // HIGH first, LOW last
                    { it.dueBy?.date },        // Then by original due date
                    { it.position }            // Then by original position
                )
            )
            
            sortedUnpositioned.forEachIndexed { index, task ->
                val newPosition = maxPosition + ((index + 1) * 1000)
                val updatedTask = task.copy(
                    overduePosition = newPosition,
                    updatedAt = Instant.now()
                )
                taskRepository.save(updatedTask)
                
                // Log the positioning
                println("📍 OVERDUE POSITION ASSIGNED - '${task.title}' at position $newPosition")
            }
        }
    }
    
    /**
     * Calculate position after a task in overdue context
     */
    private fun calculateOverduePositionAfter(afterTask: Task, userId: String): Int {
        val afterPosition = afterTask.overduePosition ?: 0
        val todayDateStr = java.time.LocalDate.now().toString()
        // Find next overdue task
        val allOverdue = taskRepository.findByUserIdAndCompletedOrderByPositionAsc(userId, false)
            .filter { it.isOverdue(todayDateStr) && it.overduePosition != null }
            .sortedBy { it.overduePosition }
        
        val nextTask = allOverdue.firstOrNull { (it.overduePosition ?: 0) > afterPosition }
        
        return if (nextTask != null) {
            val nextPosition = nextTask.overduePosition ?: (afterPosition + 2000)
            (afterPosition + nextPosition) / 2
        } else {
            afterPosition + 1000
        }
    }
    
    /**
     * Calculate position before a task in overdue context
     */
    private fun calculateOverduePositionBefore(beforeTask: Task, userId: String): Int {
        val beforePosition = beforeTask.overduePosition ?: 0
        val todayDateStr = java.time.LocalDate.now().toString()
        // Find previous overdue task
        val allOverdue = taskRepository.findByUserIdAndCompletedOrderByPositionAsc(userId, false)
            .filter { it.isOverdue(todayDateStr) && it.overduePosition != null }
            .sortedBy { it.overduePosition }
        
        val prevTask = allOverdue.lastOrNull { (it.overduePosition ?: 0) < beforePosition }
        
        return if (prevTask != null) {
            val prevPosition = prevTask.overduePosition ?: (beforePosition - 2000)
            (prevPosition + beforePosition) / 2
        } else {
            beforePosition - 1000
        }
    }
    
    /**
     * Get minimum overdue position for a user
     */
    private fun getMinOverduePosition(userId: String): Int {
        val todayDateStr = java.time.LocalDate.now().toString()
        return taskRepository.findByUserIdAndCompletedOrderByPositionAsc(userId, false)
            .filter { it.isOverdue(todayDateStr) && it.overduePosition != null }
            .minOfOrNull { it.overduePosition!! } ?: 1000
    }
    
    /**
     * Get maximum overdue position for a user
     */
    private fun getMaxOverduePosition(userId: String): Int {
        val todayDateStr = java.time.LocalDate.now().toString()
        return taskRepository.findByUserIdAndCompletedOrderByPositionAsc(userId, false)
            .filter { it.isOverdue(todayDateStr) && it.overduePosition != null }
            .maxOfOrNull { it.overduePosition!! } ?: 0
    }
    
    private fun findNextTaskInContext(userId: String, context: String, afterPosition: Int): Task? {
        return when {
            context.startsWith("date:") -> {
                val dateStr = context.substringAfter("date:")
                // Find tasks for this date ordered by position
                taskRepository.findByUserIdAndDueByDateOrderByPositionAsc(userId, dateStr)
                    .firstOrNull { it.position > afterPosition }
            }
            context == "inbox" -> {
                taskRepository.findByUserIdAndDueByIsNullOrderByPositionAsc(userId)
                    .firstOrNull { task -> task.position > afterPosition }
            }
            else -> null
        }
    }
    
    private fun countTodayCompletions(userId: String): Long {
        val todayStart = java.time.LocalDate.now()
            .atStartOfDay()
            .toInstant(java.time.ZoneOffset.UTC)
        val todayEnd = todayStart.plus(java.time.Duration.ofDays(1))
        
        return taskRepository.findByUserIdAndCompletedTrueAndCompletedAtBetween(userId, todayStart, todayEnd).size.toLong()
    }
}

data class TaskStats(
    val total: Long,
    val completed: Long,
    val pending: Long
)

// Extension function for repository
fun TaskRepository.countByUserId(userId: String): Long {
    return this.countByUserIdAndCompleted(userId, true) + this.countByUserIdAndCompleted(userId, false)
}

