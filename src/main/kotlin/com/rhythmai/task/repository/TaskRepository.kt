package com.rhythmai.task.repository

import com.rhythmai.task.model.Priority
import com.rhythmai.task.model.Task
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface TaskRepository : MongoRepository<Task, String> {
    
    fun findByUserIdOrderByPositionAsc(userId: String): List<Task>
    
    fun findByUserIdOrderByPositionAsc(userId: String, pageable: Pageable): Page<Task>
    
    fun findByUserIdAndCompletedOrderByPositionAsc(userId: String, completed: Boolean): List<Task>
    
    fun findByUserIdAndCompletedOrderByPositionAsc(userId: String, completed: Boolean, pageable: Pageable): Page<Task>
    
    fun findByUserIdAndPriorityOrderByPositionAsc(userId: String, priority: Priority): List<Task>
    
    // Legacy method - commented out after hybrid date migration
    // fun findByUserIdAndDueDateBetweenOrderByDueDateAsc(
    //     userId: String, 
    //     start: Instant, 
    //     end: Instant
    // ): List<Task>
    
    // Legacy method - commented out after hybrid date migration
    // fun findByUserIdAndDueDateLessThanEqualAndCompletedFalseOrderByDueDateAsc(
    //     userId: String, 
    //     dueDate: Instant
    // ): List<Task>
    
    fun findByUserIdAndTagsContainingOrderByPositionAsc(userId: String, tag: String): List<Task>
    
    // Position management methods
    fun findTopByUserIdOrderByPositionDesc(userId: String): Task?
    
    // Legacy method - commented out after hybrid date migration
    // Get max position for specific date
    // fun findTopByUserIdAndDueDateBetweenOrderByPositionDesc(
    //     userId: String, 
    //     startDate: Instant, 
    //     endDate: Instant
    // ): Task?
    
    // Legacy method - commented out after hybrid date migration
    // Get tasks ordered by position for specific date
    // fun findByUserIdAndDueDateBetweenOrderByPositionAsc(
    //     userId: String,
    //     startDate: Instant,
    //     endDate: Instant
    // ): List<Task>
    
    // Get tasks without due date (inbox)
    fun findByUserIdAndDueByIsNullOrderByPositionAsc(userId: String): List<Task>
    
    // Get tasks for a specific date (all-day tasks), ordered by position
    @Query("{'userId': ?0, 'dueBy.date': ?1}")
    fun findByUserIdAndDueByDate(userId: String, date: String): List<Task>
    
    // Get tasks for a specific date ordered by position
    @Query("{'userId': ?0, 'dueBy.date': ?1}")
    fun findByUserIdAndDueByDateOrderByPositionAsc(userId: String, date: String): List<Task>
    
    @Query("{'userId': ?0, 'title': {'\$regex': ?1, '\$options': 'i'}}")
    fun findByUserIdAndTitleContainingIgnoreCase(userId: String, title: String): List<Task>
    
    @Query("{'userId': ?0, '\$or': [" +
           "{'title': {'\$regex': ?1, '\$options': 'i'}}, " +
           "{'description': {'\$regex': ?1, '\$options': 'i'}}" +
           "]}")
    fun searchByUserIdAndText(userId: String, searchText: String): List<Task>
    
    fun countByUserIdAndCompleted(userId: String, completed: Boolean): Long
    
    // Analytics support - completion tracking
    fun findByUserIdAndCompletedTrueAndCompletedAtBetween(
        userId: String, 
        start: Instant, 
        end: Instant
    ): List<Task>
    
    // View-based filtering methods
    
    // Inbox: no date, no project  
    fun findByUserIdAndDueByIsNullAndProjectIdIsNullAndCompletedOrderByPositionAsc(
        userId: String,
        completed: Boolean,
        pageable: Pageable
    ): Page<Task>
    
    // Inbox with today's completed: (no date + no project) OR (completed today)
    // Supports both new completedOn.date and legacy completedDate fields
    @Query("{'userId': ?0, '\$or': [" +
           "{'dueBy': null, 'projectId': null, 'completed': ?1}, " +  // Regular inbox tasks
           "{'completedOn.date': ?2, 'completed': true}, " +  // Today's completed (new format)
           "{'completedDate': ?2, 'completed': true}" +  // Today's completed (legacy format)
           "]}")
    fun findInboxTasksIncludingTodayCompleted(
        userId: String,
        completed: Boolean,
        todayDateStr: String,
        pageable: Pageable
    ): Page<Task>
    
    // Today + overdue tasks (nested DueBy structure)
    @Query("{'userId': ?0, '\$or': [" +
           "{'dueBy.date': ?1, 'dueBy.time': null}, " +  // All-day tasks due today
           "{'dueBy.date': {'\$lt': ?1}, 'dueBy.time': null, 'completed': false}, " +  // All-day overdue (incomplete only)
           "{'dueBy.time': {'\$gte': ?2, '\$lt': ?3}}, " +  // Time-specific tasks due today
           "{'dueBy.time': {'\$lt': ?2}, 'completed': false}" +  // Time-specific overdue (incomplete only)
           "], 'completed': ?4}")
    fun findTodayTasks(
        userId: String,
        todayDateStr: String,    // "2025-09-06" for date-only comparisons
        todayStart: Instant,     // Start of today in user TZ as UTC
        todayEnd: Instant,       // End of today in user TZ as UTC
        completed: Boolean,
        pageable: Pageable
    ): Page<Task>
    
    // Today view: ALL tasks due today (completed or not) + incomplete overdue tasks + completed today (nested DueBy)
    @Query("{'userId': ?0, '\$or': [" +
           "{'dueBy.date': ?1, 'dueBy.time': null}, " +  // All-day tasks due today
           "{'dueBy.date': {'\$lt': ?1}, 'dueBy.time': null, 'completed': false}, " +  // All-day overdue (incomplete only)
           "{'dueBy.time': {'\$gte': ?2, '\$lt': ?3}}, " +  // Time-specific tasks due today
           "{'dueBy.time': {'\$lt': ?2}, 'completed': false}, " +  // Time-specific overdue (incomplete only)
           "{'completedOn.date': ?1, 'completed': true}, " +  // Tasks completed today (new format)
           "{'completedDate': ?1, 'completed': true}" +  // Tasks completed today (legacy format)
           "]}")
    fun findAllTodayViewTasks(
        userId: String,
        todayDateStr: String,    // "2025-09-06" for date-only comparisons
        todayStart: Instant,     // Start of today in user TZ as UTC
        todayEnd: Instant,       // End of today in user TZ as UTC
        pageable: Pageable
    ): Page<Task>
    
    // Upcoming tasks (future) - nested DueBy
    @Query("{'userId': ?0, '\$or': [" +
           "{'dueBy.date': {'\$gt': ?1}, 'dueBy.time': null}, " +  // All-day future tasks
           "{'dueBy.time': {'\$gte': ?2}}" +  // Time-specific future tasks
           "], 'completed': ?3}")
    fun findUpcomingTasks(
        userId: String,
        todayDateStr: String,    // Today's date to find tasks after
        tomorrowStart: Instant,  // Start of tomorrow in user TZ as UTC
        completed: Boolean,
        pageable: Pageable
    ): Page<Task>
    
    // Count for analytics
    fun countByUserId(userId: String): Long
    
    // Count completed today - supports both new and legacy formats
    @Query(value = "{'userId': ?0, 'completed': true, '\$or': [" +
           "{'completedOn.date': ?1}, " +  // New format
           "{'completedDate': ?1}" +  // Legacy format
           "]}", count = true)
    fun countByUserIdAndCompletedTrueAndCompletedOnDateEquals(
        userId: String,
        dateStr: String
    ): Long
    
    // Legacy method for backward compatibility
    @Deprecated("Use countByUserIdAndCompletedTrueAndCompletedOnDateEquals instead")
    fun countByUserIdAndCompletedTrueAndCompletedAtBetween(
        userId: String,
        start: Instant,
        end: Instant
    ): Long
    
    // Updated search and filter methods with completed parameter
    fun findByUserIdAndTagsContainingAndCompletedOrderByPositionAsc(
        userId: String,
        tag: String,
        completed: Boolean
    ): List<Task>
    
    fun findByUserIdAndPriorityAndCompletedOrderByPositionAsc(
        userId: String,
        priority: Priority,
        completed: Boolean
    ): List<Task>
    
    @Query("{'userId': ?0, '\$or': [" +
           "{'title': {'\$regex': ?1, '\$options': 'i'}}, " +
           "{'description': {'\$regex': ?1, '\$options': 'i'}}" +
           "], 'completed': ?2}")
    fun searchByUserIdAndTextAndCompleted(
        userId: String,
        searchText: String,
        completed: Boolean
    ): List<Task>
}