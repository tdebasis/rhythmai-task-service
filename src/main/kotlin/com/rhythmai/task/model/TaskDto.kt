package com.rhythmai.task.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.Min
import java.time.Instant

data class CreateTaskRequest(
    @field:NotBlank(message = "Title is required")
    @field:Size(max = 255, message = "Title must be less than 255 characters")
    val title: String,
    
    @field:Size(max = 10000, message = "Description must be less than 10000 characters")
    val description: String? = null,  // Markdown content
    
    val projectId: String? = null,  // Optional project association
    val priority: Priority = Priority.MEDIUM,
    val dueDate: Instant? = null,  // UTC timestamp
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
    val dueDate: Instant? = null,
    val tags: List<String>? = null,
    val position: Int? = null,
    
    // Position control hints
    val insertAfterTaskId: String? = null,  // Insert after specific task
    val insertAtTop: Boolean = false        // Insert at top of context
)

data class TaskResponse(
    val id: String,
    val title: String,
    val description: String?,  // Markdown content
    val projectId: String?,
    val completed: Boolean,
    val priority: Priority,
    val dueDate: Instant?,
    val tags: List<String>,
    val position: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?
) {
    companion object {
        fun from(task: Task): TaskResponse {
            return TaskResponse(
                id = task.id!!,
                title = task.title,
                description = task.description,
                projectId = task.projectId,
                completed = task.completed,
                priority = task.priority,
                dueDate = task.dueDate,
                tags = task.tags,
                position = task.position,
                createdAt = task.createdAt,
                updatedAt = task.updatedAt,
                completedAt = task.completedAt
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