package com.rhythmai.task.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "rhythmai-tasks")
data class Task(
    @Id
    val id: String? = null,
    
    // Ownership & Organization
    val userId: String,
    val projectId: String? = null,  // Optional project association
    
    // Core content
    val title: String,
    val description: String? = null,  // Markdown formatted content
    
    // Status
    val completed: Boolean = false,
    val priority: Priority = Priority.MEDIUM,
    
    // Scheduling (UTC storage)
    val dueDate: Instant? = null,
    
    // Organization
    val tags: List<String> = emptyList(),
    val position: Int = 0,  // For manual ordering within same date/project
    
    // Timestamps (UTC)
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val completedAt: Instant? = null
)

enum class Priority {
    LOW, MEDIUM, HIGH
}