package com.havq.task.model

/**
 * Defines how task times behave across timezones.
 * 
 * This enum supports both fixed and floating time concepts similar to calendar systems.
 * Currently only FIXED is supported, with FLOATING reserved for future implementation.
 */
enum class TimeType {
    /**
     * Fixed time - represents an absolute moment in time.
     * The task occurs at the same UTC instant regardless of timezone.
     * Example: A meeting at 2 PM EST shows as 7 PM GMT, 11 PM PST
     * Use case: Meetings, coordinated deadlines, shared events
     */
    FIXED,
    
    /**
     * Floating time - represents a local time that moves with the user.
     * The task stays at the same local time when traveling across timezones.
     * Example: "Morning workout at 7 AM" stays 7 AM whether in NYC or Tokyo
     * Use case: Personal routines, habits, self-care tasks
     * Status: Reserved for future implementation
     */
    FLOATING
}