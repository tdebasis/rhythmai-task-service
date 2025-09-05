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
    
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<Task>
    
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<Task>
    
    fun findByUserIdAndCompletedOrderByCreatedAtDesc(userId: String, completed: Boolean): List<Task>
    
    fun findByUserIdAndCompletedOrderByCreatedAtDesc(userId: String, completed: Boolean, pageable: Pageable): Page<Task>
    
    fun findByUserIdAndPriorityOrderByCreatedAtDesc(userId: String, priority: Priority): List<Task>
    
    fun findByUserIdAndDueDateBetweenOrderByDueDateAsc(
        userId: String, 
        start: Instant, 
        end: Instant
    ): List<Task>
    
    fun findByUserIdAndDueDateLessThanEqualAndCompletedFalseOrderByDueDateAsc(
        userId: String, 
        dueDate: Instant
    ): List<Task>
    
    fun findByUserIdAndTagsContainingOrderByCreatedAtDesc(userId: String, tag: String): List<Task>
    
    // Position management methods
    fun findTopByUserIdOrderByPositionDesc(userId: String): Task?
    
    // Get max position for specific date
    fun findTopByUserIdAndDueDateBetweenOrderByPositionDesc(
        userId: String, 
        startDate: Instant, 
        endDate: Instant
    ): Task?
    
    // Get tasks ordered by position for specific date
    fun findByUserIdAndDueDateBetweenOrderByPositionAsc(
        userId: String,
        startDate: Instant,
        endDate: Instant
    ): List<Task>
    
    // Get tasks without due date (inbox)
    fun findByUserIdAndDueDateIsNullOrderByPositionAsc(userId: String): List<Task>
    
    @Query("{'userId': ?0, 'title': {\$regex: ?1, \$options: 'i'}}")
    fun findByUserIdAndTitleContainingIgnoreCase(userId: String, title: String): List<Task>
    
    @Query("{'userId': ?0, \$or: [" +
           "{'title': {\$regex: ?1, \$options: 'i'}}, " +
           "{'description': {\$regex: ?1, \$options: 'i'}}" +
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
    fun findByUserIdAndDueDateIsNullAndProjectIdIsNullAndCompletedOrderByPositionAsc(
        userId: String,
        completed: Boolean,
        pageable: Pageable
    ): Page<Task>
    
    // Today + overdue tasks
    @Query("{'userId': ?0, \$or: [" +
           "{'dueDate': {\$gte: ?1, \$lt: ?2}}, " +
           "{'dueDate': {\$lt: ?1}, 'completed': false}" +
           "], 'completed': ?3}")
    fun findTodayTasks(
        userId: String,
        todayStart: Instant,
        todayEnd: Instant,
        completed: Boolean,
        pageable: Pageable
    ): Page<Task>
    
    // Upcoming tasks (future)
    fun findByUserIdAndDueDateGreaterThanEqualAndCompletedOrderByDueDateAscPositionAsc(
        userId: String,
        startDate: Instant,
        completed: Boolean,
        pageable: Pageable
    ): Page<Task>
    
    // Count for analytics
    fun countByUserId(userId: String): Long
    
    // Count completed today
    fun countByUserIdAndCompletedTrueAndCompletedAtBetween(
        userId: String,
        start: Instant,
        end: Instant
    ): Long
    
    // Updated search and filter methods with completed parameter
    fun findByUserIdAndTagsContainingAndCompletedOrderByCreatedAtDesc(
        userId: String,
        tag: String,
        completed: Boolean
    ): List<Task>
    
    fun findByUserIdAndPriorityAndCompletedOrderByCreatedAtDesc(
        userId: String,
        priority: Priority,
        completed: Boolean
    ): List<Task>
    
    @Query("{'userId': ?0, \$or: [" +
           "{'title': {\$regex: ?1, \$options: 'i'}}, " +
           "{'description': {\$regex: ?1, \$options: 'i'}}" +
           "], 'completed': ?2}")
    fun searchByUserIdAndTextAndCompleted(
        userId: String,
        searchText: String,
        completed: Boolean
    ): List<Task>
}