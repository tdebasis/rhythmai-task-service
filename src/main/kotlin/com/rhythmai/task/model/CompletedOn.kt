package com.rhythmai.task.model

import java.time.Instant

/**
 * Represents the completion date/time information for a task.
 * Encapsulates all temporal aspects of when a task was completed.
 * 
 * This is stored as an embedded document in MongoDB, following the same
 * pattern as DueBy for consistency.
 */
data class CompletedOn(
    /**
     * Date in ISO format (YYYY-MM-DD) when the task was completed.
     * This is the date in the user's timezone at the time of completion.
     * Example: "2025-09-06"
     */
    val date: String,
    
    /**
     * UTC timestamp of the exact moment the task was completed.
     * This provides precise temporal tracking for analytics and history.
     * Example: 2025-09-06T17:45:43.163519Z
     */
    val time: Instant,
    
    /**
     * Type of time handling - FIXED for completion timestamps.
     * Completions are always absolute moments in time.
     */
    val timeType: TimeType = TimeType.FIXED
) {
    companion object {
        /**
         * Create a CompletedOn for the current moment
         */
        fun now(userTimezone: String = "UTC"): CompletedOn {
            val userZone = java.time.ZoneId.of(userTimezone)
            val now = Instant.now()
            val dateInUserZone = java.time.LocalDate.now(userZone).toString()
            
            return CompletedOn(
                date = dateInUserZone,
                time = now,
                timeType = TimeType.FIXED
            )
        }
    }
}