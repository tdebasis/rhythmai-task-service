package com.rhythmai.task.model

import java.time.Instant

/**
 * Represents the due date/time information for a task.
 * Encapsulates all temporal aspects including date, time, and time type.
 * 
 * This is stored as an embedded document in MongoDB.
 */
data class DueBy(
    /**
     * Date in ISO format (YYYY-MM-DD).
     * For all-day tasks, this is the only required field.
     * Example: "2025-09-05"
     */
    val date: String,
    
    /**
     * Optional UTC timestamp for time-specific tasks.
     * Null for all-day tasks, Instant for time-specific tasks.
     * Example: 2025-09-05T21:00:00Z (2pm PST converted to UTC)
     */
    val time: Instant? = null,
    
    /**
     * Type of time handling - FIXED or FLOATING.
     * FIXED: Absolute time that doesn't change with user's timezone
     * FLOATING: Time that adjusts to user's current timezone (future feature)
     */
    val timeType: TimeType = TimeType.FIXED
) {
    /**
     * Convenience property to check if this is an all-day task
     */
    val isAllDay: Boolean
        get() = time == null
    
    /**
     * Convenience property to check if this is a time-specific task
     */
    val isTimeSpecific: Boolean
        get() = time != null
    
    companion object {
        /**
         * Create a DueBy for an all-day task
         */
        fun allDay(date: String): DueBy {
            return DueBy(
                date = date,
                time = null,
                timeType = TimeType.FIXED
            )
        }
        
        /**
         * Create a DueBy for a time-specific task
         */
        fun withTime(date: String, time: Instant, timeType: TimeType = TimeType.FIXED): DueBy {
            return DueBy(
                date = date,
                time = time,
                timeType = timeType
            )
        }
    }
}