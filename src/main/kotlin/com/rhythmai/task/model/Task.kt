package com.rhythmai.task.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document(collection = "rhythmai-tasks")
data class Task(
    @Id
    val id: String? = null,
    val userId: String,
    val title: String,
    val description: String? = null,
    val completed: Boolean = false,
    val priority: Priority = Priority.MEDIUM,
    val dueDate: LocalDateTime? = null,
    val tags: List<String> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val completedAt: LocalDateTime? = null
)

enum class Priority {
    LOW, MEDIUM, HIGH
}