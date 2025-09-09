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
    val completedOn: CompletedOn? = null,  // Null if not completed, contains date/time when completed
    
    // Legacy fields for backward compatibility (will be migrated to completedOn)
    @Deprecated("Use completedOn instead")
    val completedAt: Instant? = null,
    @Deprecated("Use completedOn instead")
    val completedDate: String? = null
) {
    /**
     * Get the effective completion timestamp, preferring completedOn over legacy fields
     */
    fun getEffectiveCompletedTime(): Instant? {
        return completedOn?.time ?: completedAt
    }
    
    /**
     * Get the effective completion date, preferring completedOn over legacy fields
     */
    fun getEffectiveCompletedDate(): String? {
        return completedOn?.date ?: completedDate
    }
    
    /**
     * Migrate legacy fields to new CompletedOn structure
     */
    fun migrateCompletionFields(): Task {
        return if (completedOn == null && (completedAt != null || completedDate != null)) {
            // Migrate from legacy fields
            val migratedCompletedOn = when {
                completedAt != null && completedDate != null -> {
                    CompletedOn(
                        date = completedDate,
                        time = completedAt,
                        timeType = TimeType.FIXED
                    )
                }
                completedAt != null -> {
                    // Only have timestamp, derive date in UTC
                    val utcDate = java.time.LocalDate.ofInstant(completedAt, java.time.ZoneOffset.UTC).toString()
                    CompletedOn(
                        date = utcDate,
                        time = completedAt,
                        timeType = TimeType.FIXED
                    )
                }
                else -> null
            }
            
            this.copy(
                completedOn = migratedCompletedOn,
                completedAt = null,
                completedDate = null
            )
        } else {
            this // Already migrated or not completed
        }
    }
    
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