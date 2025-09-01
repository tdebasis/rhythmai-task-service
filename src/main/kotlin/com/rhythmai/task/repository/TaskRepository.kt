package com.rhythmai.task.repository

import com.rhythmai.task.model.Priority
import com.rhythmai.task.model.Task
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface TaskRepository : MongoRepository<Task, String> {
    
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<Task>
    
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<Task>
    
    fun findByUserIdAndCompletedOrderByCreatedAtDesc(userId: String, completed: Boolean): List<Task>
    
    fun findByUserIdAndCompletedOrderByCreatedAtDesc(userId: String, completed: Boolean, pageable: Pageable): Page<Task>
    
    fun findByUserIdAndPriorityOrderByCreatedAtDesc(userId: String, priority: Priority): List<Task>
    
    fun findByUserIdAndDueDateBetweenOrderByDueDateAsc(
        userId: String, 
        start: LocalDateTime, 
        end: LocalDateTime
    ): List<Task>
    
    fun findByUserIdAndDueDateLessThanEqualAndCompletedFalseOrderByDueDateAsc(
        userId: String, 
        dueDate: LocalDateTime
    ): List<Task>
    
    fun findByUserIdAndTagsContainingOrderByCreatedAtDesc(userId: String, tag: String): List<Task>
    
    @Query("{'userId': ?0, 'title': {\$regex: ?1, \$options: 'i'}}")
    fun findByUserIdAndTitleContainingIgnoreCase(userId: String, title: String): List<Task>
    
    @Query("{'userId': ?0, \$or: [" +
           "{'title': {\$regex: ?1, \$options: 'i'}}, " +
           "{'description': {\$regex: ?1, \$options: 'i'}}" +
           "]}")
    fun searchByUserIdAndText(userId: String, searchText: String): List<Task>
    
    fun countByUserIdAndCompleted(userId: String, completed: Boolean): Long
}