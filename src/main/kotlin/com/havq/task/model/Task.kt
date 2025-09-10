package com.havq.task.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "havq-tasks")
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
    
    // Scheduling - Complex type encapsulating all temporal information
    val dueBy: DueBy? = null,  // Null for inbox tasks, contains date/time/timezone/type for scheduled tasks
    
    // Organization
    val tags: List<String> = emptyList(),
    val position: Int = 0,  // For manual ordering within same date/project
    
    // Timestamps (UTC)
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    
    // Overdue positioning - for maintaining order of overdue tasks
    val overduePosition: Int? = null,  // Position when task is in overdue context
    
    // Completion information - Complex type encapsulating all completion temporal information
    val completedOn: CompletedOn? = null  // Null if not completed, contains date/time when completed
) {
    /**
     * Check if task is overdue (past due date and not completed)
     */
    fun isOverdue(currentDate: String = java.time.LocalDate.now().toString()): Boolean {
        if (completed) return false
        val taskDate = dueBy?.date ?: return false
        return taskDate < currentDate
    }
}

enum class Priority {
    LOW, MEDIUM, HIGH
}