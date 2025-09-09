package com.rhythmai.task.controller

import com.rhythmai.task.model.*
import com.rhythmai.task.security.AuthUtils
import com.rhythmai.task.service.TaskService
import com.rhythmai.task.service.TaskStats
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.Duration

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Task management operations")
class TaskController(
    private val taskService: TaskService,
    private val authUtils: AuthUtils
) {
    
    @GetMapping("/hello-world")
    @Operation(summary = "Hello World", description = "Simple test endpoint to verify API connectivity")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Successful response",
            content = [Content(mediaType = "application/json",
                schema = Schema(example = "{\"message\": \"hello world !!\"}"))])
    ])
    fun helloWorld(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("message" to "hello world !!"))
    }
    
    @PostMapping
    @Operation(
        summary = "Create Task", 
        description = """
            Create a new task for the authenticated user.
            
            **Context Parameter:**
            The optional 'context' parameter tracks where the task was created from for analytics:
            - **inbox**: Task created from inbox view (typically without due date)
            - **today**: Task created from today view (typically with today's date)
            - **upcoming**: Task created from upcoming view (typically with future date)
            
            This helps understand user behavior and optimize the UI experience.
        """
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Task created successfully",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = TaskResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request data (e.g., missing title)"),
        ApiResponse(responseCode = "401", description = "Authentication required")
    ])
    fun createTask(
        request: HttpServletRequest,
        @Parameter(description = "User ID from BFF", required = true, example = "test-user-123")
        @RequestHeader("X-User-ID") userId: String,
        @Parameter(description = "User email from BFF", required = true, example = "test@example.com")
        @RequestHeader("X-User-Email") userEmail: String,
        @Parameter(description = "User name from BFF", required = true, example = "Test User")
        @RequestHeader("X-User-Name") userName: String,
        @Parameter(description = "User timezone from BFF", required = false, example = "America/Los_Angeles")
        @RequestHeader("X-User-Timezone", required = false) userTimezone: String?,
        @Parameter(
            description = """
                Creation context for analytics tracking. Indicates which view the task was created from.
                Used to understand user workflows and optimize the UI experience.
                Does not affect task properties - only used for analytics.
            """,
            example = "inbox"
        ) 
        @RequestParam(required = false) context: String?,
        @Valid @RequestBody 
        @Parameter(description = "Task creation request") createRequest: CreateTaskRequest
    ): ResponseEntity<TaskResponse> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        if (!authUtils.validateUserContext(userContext)) {
            throw UnauthorizedException("Invalid user context")
        }
        
        val timezone = userTimezone ?: "UTC"
        val task = taskService.createTask(userContext.userId, createRequest, context, userContext.email, timezone)
        return ResponseEntity.status(HttpStatus.CREATED).body(task)
    }
    
    @GetMapping
    @Operation(
        summary = "Get Tasks", 
        description = """
            Retrieve tasks with optional view filtering and pagination.
            
            **View Parameter Behavior:**
            - **inbox**: Returns unorganized tasks (no due date AND no project). These are tasks captured for later processing.
            - **today**: Returns ALL tasks due today (both completed and incomplete) PLUS any overdue incomplete tasks. UI can show/hide completed.
            - **upcoming**: Returns future tasks (due date >= tomorrow). Sorted by due date then position.
            - **null/omitted**: Returns all incomplete tasks (default behavior when no view specified).
            
            **Default Behavior:**
            When no view parameter is provided, returns all tasks with completed=false (default).
            
            **Date Handling:**
            - Tasks are date-based, not time-based
            - "Today" means the current UTC date
            - Time zones don't affect task dates - a task due Sept 5 is due Sept 5 everywhere
            
            **Sorting:**
            - Inbox: Sorted by position
            - Today: Sorted by due date (overdue first) then position
            - Upcoming: Sorted by due date then position
            - Default: Sorted by created date descending
        """
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Tasks retrieved successfully"),
        ApiResponse(responseCode = "400", description = "Invalid view parameter value"),
        ApiResponse(responseCode = "401", description = "Authentication required")
    ])
    fun getAllTasks(
        request: HttpServletRequest,
        @Parameter(description = "User ID from BFF", required = true, example = "test-user-123")
        @RequestHeader("X-User-ID") userId: String,
        @Parameter(description = "User email from BFF", required = true, example = "test@example.com")
        @RequestHeader("X-User-Email") userEmail: String,
        @Parameter(description = "User name from BFF", required = true, example = "Test User")
        @RequestHeader("X-User-Name") userName: String,
        @Parameter(description = "User timezone from BFF", required = false, example = "America/Los_Angeles")
        @RequestHeader("X-User-Timezone", required = false) userTimezone: String?,
        @Parameter(
            description = """
                View filter to retrieve specific task subsets:
                - **inbox**: Unorganized tasks (no due date, no project) waiting to be processed
                - **today**: Tasks due today + overdue tasks that need immediate attention
                - **upcoming**: Future tasks (tomorrow onwards) for planning ahead
                - **omitted**: All tasks matching other filters (default behavior)
            """, 
            schema = Schema(allowableValues = ["inbox", "today", "upcoming"])
        )
        @RequestParam(required = false) view: String?,
        @Parameter(
            description = "Page number for pagination (0-based indexing)",
            example = "0"
        ) 
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(
            description = "Number of tasks per page (max 100)",
            example = "20"
        ) 
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(
            description = """
                Filter by completion status. 
                - **true**: Show only completed tasks
                - **false**: Show only incomplete tasks (default)
                Works in combination with view parameter.
            """
        ) 
        @RequestParam(required = false, defaultValue = "false") completed: Boolean,
        @Parameter(
            description = "Filter by priority level (HIGH, MEDIUM, LOW). Can be combined with view parameter.",
            example = "HIGH"
        ) 
        @RequestParam(required = false) priority: Priority?,
        @Parameter(
            description = "Filter by tag. Returns tasks containing this tag. Can be combined with view parameter.",
            example = "urgent"
        ) 
        @RequestParam(required = false) tag: String?,
        @Parameter(
            description = "Search text in task title and description. Overrides view parameter when provided.",
            example = "meeting"
        ) 
        @RequestParam(required = false) search: String?
    ): ResponseEntity<*> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        // Add sorting by position for consistent ordering
        val pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("position"))
        
        // Track view usage for analytics
        view?.let {
            taskService.trackViewContext(userContext.userId, it)
        }
        
        // Handle view-based filtering
        val timezone = userTimezone ?: "UTC"
        return when (view) {
            "inbox" -> {
                // Inbox: tasks without due date and without project, plus today's completed tasks
                val tasks = taskService.getInboxTasks(userContext.userId, completed, pageable, timezone)
                ResponseEntity.ok(tasks)
            }
            "today" -> {
                // Today: ALL tasks due today (completed & incomplete) + overdue incomplete tasks
                // Note: completed parameter is ignored for today view - UI decides what to show
                val tasks = taskService.getTodayTasks(userContext.userId, completed, pageable, timezone)
                ResponseEntity.ok(tasks)
            }
            "upcoming" -> {
                // Upcoming: future tasks (tomorrow onwards)
                val tasks = taskService.getUpcomingTasks(userContext.userId, completed, pageable, timezone)
                ResponseEntity.ok(tasks)
            }
            null -> {
                // No view specified - apply standard filters
                when {
                    search != null -> {
                        val tasks = taskService.searchTasks(userContext.userId, search, completed)
                        ResponseEntity.ok(tasks)
                    }
                    tag != null -> {
                        val tasks = taskService.getTasksByTag(userContext.userId, tag, completed)
                        ResponseEntity.ok(tasks)
                    }
                    priority != null -> {
                        val tasks = taskService.getTasksByPriority(userContext.userId, priority, completed)
                        ResponseEntity.ok(tasks)
                    }
                    else -> {
                        // Default: all tasks with completed filter (defaults to false)
                        val taskList = taskService.getTasksByCompleted(userContext.userId, completed, pageable, timezone)
                        ResponseEntity.ok(taskList)
                    }
                }
            }
            else -> {
                // Invalid view parameter
                throw BadRequestException("Invalid view parameter. Valid values: inbox, today, upcoming")
            }
        }
    }
    
    @GetMapping("/{id}")
    fun getTask(
        request: HttpServletRequest,
        @PathVariable id: String
    ): ResponseEntity<TaskResponse> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val task = taskService.getTask(userContext.userId, id)
            ?: throw TaskNotFoundException("Task not found")
        
        return ResponseEntity.ok(task)
    }
    
    @PutMapping("/{id}")
    fun updateTask(
        request: HttpServletRequest,
        @Parameter(description = "User ID from BFF", required = true, example = "test-user-123")
        @RequestHeader("X-User-ID") userId: String,
        @Parameter(description = "User email from BFF", required = true, example = "test@example.com")
        @RequestHeader("X-User-Email") userEmail: String,
        @Parameter(description = "User name from BFF", required = true, example = "Test User")
        @RequestHeader("X-User-Name") userName: String,
        @Parameter(description = "User timezone from BFF", required = false, example = "America/Los_Angeles")
        @RequestHeader("X-User-Timezone", required = false) userTimezone: String?,
        @PathVariable id: String,
        @Valid @RequestBody updateRequest: UpdateTaskRequest
    ): ResponseEntity<TaskResponse> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val timezone = userTimezone ?: "UTC"
        val task = taskService.updateTask(userContext.userId, id, updateRequest, userContext.email, timezone)
            ?: throw TaskNotFoundException("Task not found")
        
        return ResponseEntity.ok(task)
    }
    
    @DeleteMapping("/{id}")
    fun deleteTask(
        request: HttpServletRequest,
        @PathVariable id: String
    ): ResponseEntity<Void> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val deleted = taskService.deleteTask(userContext.userId, id)
        if (!deleted) {
            throw TaskNotFoundException("Task not found")
        }
        
        return ResponseEntity.noContent().build()
    }
    
    @GetMapping("/upcoming")
    fun getUpcomingTasks(
        request: HttpServletRequest,
        @RequestParam(required = false) days: Int?
    ): ResponseEntity<List<TaskResponse>> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val daysAhead = days ?: 7
        val now = Instant.now()
        val end = now.plus(Duration.ofDays(daysAhead.toLong()))
        
        val tasks = taskService.getUpcomingTasks(userContext.userId, now, end)
        return ResponseEntity.ok(tasks)
    }
    
    @GetMapping("/overdue")
    fun getOverdueTasks(
        request: HttpServletRequest
    ): ResponseEntity<List<TaskResponse>> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val tasks = taskService.getOverdueTasks(userContext.userId)
        return ResponseEntity.ok(tasks)
    }
    
    @GetMapping("/stats")
    fun getTaskStats(
        request: HttpServletRequest
    ): ResponseEntity<TaskStats> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val stats = taskService.getTaskStats(userContext.userId)
        return ResponseEntity.ok(stats)
    }
    
    @PatchMapping("/{id}/complete")
    fun completeTask(
        request: HttpServletRequest,
        @RequestHeader("X-User-Timezone", required = false) userTimezone: String?,
        @PathVariable id: String
    ): ResponseEntity<TaskResponse> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val timezone = userTimezone ?: "UTC"
        val updateRequest = UpdateTaskRequest(completed = true)
        val task = taskService.updateTask(userContext.userId, id, updateRequest, userContext.email, timezone)
            ?: throw TaskNotFoundException("Task not found")
        
        return ResponseEntity.ok(task)
    }
    
    @PatchMapping("/{id}/incomplete")
    fun markTaskIncomplete(
        request: HttpServletRequest,
        @RequestHeader("X-User-Timezone", required = false) userTimezone: String?,
        @PathVariable id: String
    ): ResponseEntity<TaskResponse> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val timezone = userTimezone ?: "UTC"
        val updateRequest = UpdateTaskRequest(completed = false)
        val task = taskService.updateTask(userContext.userId, id, updateRequest, userContext.email, timezone)
            ?: throw TaskNotFoundException("Task not found")
        
        return ResponseEntity.ok(task)
    }
    
    @PatchMapping("/{id}/move")
    @Operation(
        summary = "Move/reorder a task",
        description = "Move a task to a new position using various strategies (insertAfter, insertBefore, moveToTop, moveToBottom)"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Task moved successfully"),
        ApiResponse(responseCode = "400", description = "Invalid move request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "404", description = "Task or reference task not found")
    ])
    fun moveTask(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody moveRequest: MoveTaskRequest
    ): ResponseEntity<TaskResponse> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val movedTask = taskService.moveTask(
            userId = userContext.userId,
            taskId = id,
            moveRequest = moveRequest,
            userEmail = userContext.email
        ) ?: throw TaskNotFoundException("Task not found")
        
        return ResponseEntity.ok(movedTask)
    }
}