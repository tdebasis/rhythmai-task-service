package com.rhythmai.task.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.Min
import jakarta.validation.Valid
import java.time.Instant

/**
 * Request DTO for due date/time information
 */
data class DueByRequest(
    @field:NotBlank(message = "Date is required when setting due date")
    val date: String,                   // ISO date string "2025-09-05"
    val time: Instant? = null,          // UTC timestamp for time-specific tasks
    val timeType: TimeType = TimeType.FIXED  // Future: support FLOATING
)

data class CreateTaskRequest(
    @field:NotBlank(message = "Title is required")
    @field:Size(max = 255, message = "Title must be less than 255 characters")
    val title: String,
    
    @field:Size(max = 10000, message = "Description must be less than 10000 characters")
    val description: String? = null,  // Markdown content
    
    val projectId: String? = null,  // Optional project association
    val priority: Priority = Priority.MEDIUM,
    
    // Complex type for all temporal information
    @field:Valid
    val dueBy: DueByRequest? = null,    // Null for inbox tasks
    
    val tags: List<String> = emptyList(),
    
    @field:Min(0, message = "Position must be non-negative")
    val position: Int? = null,  // Optional - will auto-calculate if not provided
    
    // Position control hints
    val insertAfterTaskId: String? = null,  // Insert after specific task
    val insertAtTop: Boolean = false        // Insert at top of context
)

data class UpdateTaskRequest(
    @field:Size(max = 255, message = "Title must be less than 255 characters")
    val title: String? = null,
    
    @field:Size(max = 10000, message = "Description must be less than 10000 characters")
    val description: String? = null,  // Markdown content
    
    val projectId: String? = null,
    val completed: Boolean? = null,
    val priority: Priority? = null,
    
    // Complex type for all temporal information
    @field:Valid
    val dueBy: DueByRequest? = null,    // Null to keep existing, empty object to clear
    val clearDueDate: Boolean = false,  // Explicit flag to remove due date
    
    val tags: List<String>? = null,
    val position: Int? = null,
    
    // Position control hints
    val insertAfterTaskId: String? = null,  // Insert after specific task
    val insertAtTop: Boolean = false        // Insert at top of context
)

/**
 * Response DTO for due date/time information
 */
data class DueByResponse(
    val date: String,                   // ISO date string "2025-09-05"
    val time: Instant? = null,          // UTC timestamp for time-specific tasks
    val timeType: TimeType              // FIXED or FLOATING
) {
    companion object {
        fun from(dueBy: DueBy): DueByResponse {
            return DueByResponse(
                date = dueBy.date,
                time = dueBy.time,
                timeType = dueBy.timeType
            )
        }
    }
}

/**
 * Response DTO for completion date/time information
 */
data class CompletedOnResponse(
    val date: String,                   // ISO date string "2025-09-06" in user's timezone
    val time: Instant,                  // UTC timestamp when completed
    val timeType: TimeType              // Always FIXED for completions
) {
    companion object {
        fun from(completedOn: CompletedOn): CompletedOnResponse {
            return CompletedOnResponse(
                date = completedOn.date,
                time = completedOn.time,
                timeType = completedOn.timeType
            )
        }
    }
}

data class TaskResponse(
    val id: String,
    val title: String,
    val description: String?,  // Markdown content
    val projectId: String?,
    val completed: Boolean,
    val priority: Priority,
    
    // Complex type with all temporal info
    val dueBy: DueByResponse?,           // Null for inbox tasks
    
    val tags: List<String>,
    val position: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    
    // New completion format
    val completedOn: CompletedOnResponse?,
    
    // Legacy fields for backward compatibility
    @Deprecated("Use completedOn instead")
    val completedAt: Instant?
) {
    companion object {
        fun from(task: Task): TaskResponse {
            // Auto-migrate legacy fields to new format if needed
            val migratedTask = task.migrateCompletionFields()
            
            // For overdue tasks, use overduePosition as the position field
            // This allows the frontend to use the position field consistently
            val effectivePosition = if (migratedTask.isOverdue()) {
                migratedTask.overduePosition ?: migratedTask.position
            } else {
                migratedTask.position
            }
            
            return TaskResponse(
                id = migratedTask.id!!,
                title = migratedTask.title,
                description = migratedTask.description,
                projectId = migratedTask.projectId,
                completed = migratedTask.completed,
                priority = migratedTask.priority,
                dueBy = migratedTask.dueBy?.let { DueByResponse.from(it) },
                tags = migratedTask.tags,
                position = effectivePosition,
                createdAt = migratedTask.createdAt,
                updatedAt = migratedTask.updatedAt,
                completedOn = migratedTask.completedOn?.let { CompletedOnResponse.from(it) },
                completedAt = migratedTask.getEffectiveCompletedTime()  // Backward compatibility
            )
        }
    }
}

data class TaskListResponse(
    val tasks: List<TaskResponse>,
    val total: Long,
    val page: Int,
    val size: Int
)

/**
 * Request DTO for moving/reordering a task
 * Only one positioning strategy should be specified per request
 */
data class MoveTaskRequest(
    val insertAfter: String? = null,    // Task ID to position after
    val insertBefore: String? = null,   // Task ID to position before
    val moveToTop: Boolean = false,     // Move to top of context
    val moveToBottom: Boolean = false,  // Move to bottom of context
    val context: String? = null  // Optional context: "overdue", "today", "date", null (default)
) {
    init {
        // Validate that only one positioning strategy is specified
        val strategies = listOfNotNull(
            insertAfter?.let { "insertAfter" },
            insertBefore?.let { "insertBefore" },
            if (moveToTop) "moveToTop" else null,
            if (moveToBottom) "moveToBottom" else null
        )
        
        require(strategies.size <= 1) {
            "At most one positioning strategy can be specified (got: ${strategies.joinToString(", ")})"
        }
        
        // At least one positioning strategy must be specified
        require(strategies.isNotEmpty()) {
            "A positioning strategy must be specified (insertAfter, insertBefore, moveToTop, or moveToBottom)"
        }
    }
}