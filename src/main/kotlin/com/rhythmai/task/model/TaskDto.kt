package com.rhythmai.task.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CreateTaskRequest(
    @field:NotBlank(message = "Title is required")
    @field:Size(max = 255, message = "Title must be less than 255 characters")
    val title: String,
    
    @field:Size(max = 2000, message = "Description must be less than 2000 characters")
    val description: String? = null,
    
    val priority: Priority = Priority.MEDIUM,
    val dueDate: LocalDateTime? = null,
    val tags: List<String> = emptyList()
)

data class UpdateTaskRequest(
    @field:Size(max = 255, message = "Title must be less than 255 characters")
    val title: String? = null,
    
    @field:Size(max = 2000, message = "Description must be less than 2000 characters")
    val description: String? = null,
    
    val completed: Boolean? = null,
    val priority: Priority? = null,
    val dueDate: LocalDateTime? = null,
    val tags: List<String>? = null
)

data class TaskResponse(
    val id: String,
    val title: String,
    val description: String?,
    val completed: Boolean,
    val priority: Priority,
    val dueDate: LocalDateTime?,
    val tags: List<String>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val completedAt: LocalDateTime?
) {
    companion object {
        fun from(task: Task): TaskResponse {
            return TaskResponse(
                id = task.id!!,
                title = task.title,
                description = task.description,
                completed = task.completed,
                priority = task.priority,
                dueDate = task.dueDate,
                tags = task.tags,
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