package com.rhythmai.task.service

import com.rhythmai.task.model.*
import com.rhythmai.task.repository.TaskRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class TaskService(
    private val taskRepository: TaskRepository
) {
    
    fun createTask(userId: String, request: CreateTaskRequest): TaskResponse {
        val task = Task(
            userId = userId,
            title = request.title,
            description = request.description,
            priority = request.priority,
            dueDate = request.dueDate,
            tags = request.tags
        )
        
        val savedTask = taskRepository.save(task)
        return TaskResponse.from(savedTask)
    }
    
    fun getTask(userId: String, taskId: String): TaskResponse? {
        val task = taskRepository.findByIdOrNull(taskId)
        return if (task != null && task.userId == userId) {
            TaskResponse.from(task)
        } else {
            null
        }
    }
    
    fun getAllTasks(userId: String, pageable: Pageable): TaskListResponse {
        val page = taskRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        return TaskListResponse(
            tasks = page.content.map { TaskResponse.from(it) },
            total = page.totalElements,
            page = pageable.pageNumber,
            size = pageable.pageSize
        )
    }
    
    fun getTasksByCompleted(userId: String, completed: Boolean, pageable: Pageable): TaskListResponse {
        val page = taskRepository.findByUserIdAndCompletedOrderByCreatedAtDesc(userId, completed, pageable)
        return TaskListResponse(
            tasks = page.content.map { TaskResponse.from(it) },
            total = page.totalElements,
            page = pageable.pageNumber,
            size = pageable.pageSize
        )
    }
    
    fun getTasksByPriority(userId: String, priority: Priority): List<TaskResponse> {
        val tasks = taskRepository.findByUserIdAndPriorityOrderByCreatedAtDesc(userId, priority)
        return tasks.map { TaskResponse.from(it) }
    }
    
    fun getUpcomingTasks(userId: String, start: LocalDateTime, end: LocalDateTime): List<TaskResponse> {
        val tasks = taskRepository.findByUserIdAndDueDateBetweenOrderByDueDateAsc(userId, start, end)
        return tasks.map { TaskResponse.from(it) }
    }
    
    fun getOverdueTasks(userId: String): List<TaskResponse> {
        val now = LocalDateTime.now()
        val tasks = taskRepository.findByUserIdAndDueDateLessThanEqualAndCompletedFalseOrderByDueDateAsc(userId, now)
        return tasks.map { TaskResponse.from(it) }
    }
    
    fun getTasksByTag(userId: String, tag: String): List<TaskResponse> {
        val tasks = taskRepository.findByUserIdAndTagsContainingOrderByCreatedAtDesc(userId, tag)
        return tasks.map { TaskResponse.from(it) }
    }
    
    fun searchTasks(userId: String, searchText: String): List<TaskResponse> {
        val tasks = taskRepository.searchByUserIdAndText(userId, searchText)
        return tasks.map { TaskResponse.from(it) }
    }
    
    fun updateTask(userId: String, taskId: String, request: UpdateTaskRequest): TaskResponse? {
        val existingTask = taskRepository.findByIdOrNull(taskId)
            ?: return null
            
        if (existingTask.userId != userId) {
            return null
        }
        
        val updatedTask = existingTask.copy(
            title = request.title ?: existingTask.title,
            description = request.description ?: existingTask.description,
            completed = request.completed ?: existingTask.completed,
            priority = request.priority ?: existingTask.priority,
            dueDate = request.dueDate ?: existingTask.dueDate,
            tags = request.tags ?: existingTask.tags,
            updatedAt = LocalDateTime.now(),
            completedAt = if (request.completed == true && existingTask.completed != true) 
                LocalDateTime.now() else existingTask.completedAt
        )
        
        val savedTask = taskRepository.save(updatedTask)
        return TaskResponse.from(savedTask)
    }
    
    fun deleteTask(userId: String, taskId: String): Boolean {
        val task = taskRepository.findByIdOrNull(taskId)
            ?: return false
            
        if (task.userId != userId) {
            return false
        }
        
        taskRepository.deleteById(taskId)
        return true
    }
    
    fun getTaskStats(userId: String): TaskStats {
        val total = taskRepository.countByUserId(userId)
        val completed = taskRepository.countByUserIdAndCompleted(userId, true)
        val pending = total - completed
        
        return TaskStats(
            total = total,
            completed = completed,
            pending = pending
        )
    }
}

data class TaskStats(
    val total: Long,
    val completed: Long,
    val pending: Long
)

// Extension function for repository
fun TaskRepository.countByUserId(userId: String): Long {
    return this.countByUserIdAndCompleted(userId, true) + this.countByUserIdAndCompleted(userId, false)
}