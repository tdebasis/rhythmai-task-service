package com.rhythmai.task.controller

import com.rhythmai.task.model.*
import com.rhythmai.task.security.AuthUtils
import com.rhythmai.task.service.TaskService
import com.rhythmai.task.service.TaskStats
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/tasks")
class TaskController(
    private val taskService: TaskService,
    private val authUtils: AuthUtils
) {
    
    @PostMapping
    fun createTask(
        request: HttpServletRequest,
        @Valid @RequestBody createRequest: CreateTaskRequest
    ): ResponseEntity<TaskResponse> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        if (!authUtils.validateUserContext(userContext)) {
            throw UnauthorizedException("Invalid user context")
        }
        
        val task = taskService.createTask(userContext.userId, createRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(task)
    }
    
    @GetMapping
    fun getAllTasks(
        request: HttpServletRequest,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) completed: Boolean?,
        @RequestParam(required = false) priority: Priority?,
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) search: String?
    ): ResponseEntity<*> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val pageable = PageRequest.of(page, size)
        
        return when {
            search != null -> {
                val tasks = taskService.searchTasks(userContext.userId, search)
                ResponseEntity.ok(tasks)
            }
            tag != null -> {
                val tasks = taskService.getTasksByTag(userContext.userId, tag)
                ResponseEntity.ok(tasks)
            }
            priority != null -> {
                val tasks = taskService.getTasksByPriority(userContext.userId, priority)
                ResponseEntity.ok(tasks)
            }
            completed != null -> {
                val taskList = taskService.getTasksByCompleted(userContext.userId, completed, pageable)
                ResponseEntity.ok(taskList)
            }
            else -> {
                val taskList = taskService.getAllTasks(userContext.userId, pageable)
                ResponseEntity.ok(taskList)
            }
        }
    }
    
    @GetMapping("/{id}")
    fun getTask(
        request: HttpServletRequest,
        @PathVariable id: String
    ): ResponseEntity<TaskResponse> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val task = taskService.getTask(userContext.userId, id)
            ?: throw TaskNotFoundException("Task not found")
        
        return ResponseEntity.ok(task)
    }
    
    @PutMapping("/{id}")
    fun updateTask(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody updateRequest: UpdateTaskRequest
    ): ResponseEntity<TaskResponse> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val task = taskService.updateTask(userContext.userId, id, updateRequest)
            ?: throw TaskNotFoundException("Task not found")
        
        return ResponseEntity.ok(task)
    }
    
    @DeleteMapping("/{id}")
    fun deleteTask(
        request: HttpServletRequest,
        @PathVariable id: String
    ): ResponseEntity<Void> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val deleted = taskService.deleteTask(userContext.userId, id)
        if (!deleted) {
            throw TaskNotFoundException("Task not found")
        }
        
        return ResponseEntity.noContent().build()
    }
    
    @GetMapping("/upcoming")
    fun getUpcomingTasks(
        request: HttpServletRequest,
        @RequestParam(required = false) days: Int?
    ): ResponseEntity<List<TaskResponse>> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val daysAhead = days ?: 7
        val now = LocalDateTime.now()
        val end = now.plusDays(daysAhead.toLong())
        
        val tasks = taskService.getUpcomingTasks(userContext.userId, now, end)
        return ResponseEntity.ok(tasks)
    }
    
    @GetMapping("/overdue")
    fun getOverdueTasks(
        request: HttpServletRequest
    ): ResponseEntity<List<TaskResponse>> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val tasks = taskService.getOverdueTasks(userContext.userId)
        return ResponseEntity.ok(tasks)
    }
    
    @GetMapping("/stats")
    fun getTaskStats(
        request: HttpServletRequest
    ): ResponseEntity<TaskStats> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val stats = taskService.getTaskStats(userContext.userId)
        return ResponseEntity.ok(stats)
    }
    
    @PatchMapping("/{id}/complete")
    fun completeTask(
        request: HttpServletRequest,
        @PathVariable id: String
    ): ResponseEntity<TaskResponse> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val updateRequest = UpdateTaskRequest(completed = true)
        val task = taskService.updateTask(userContext.userId, id, updateRequest)
            ?: throw TaskNotFoundException("Task not found")
        
        return ResponseEntity.ok(task)
    }
    
    @PatchMapping("/{id}/incomplete")
    fun markTaskIncomplete(
        request: HttpServletRequest,
        @PathVariable id: String
    ): ResponseEntity<TaskResponse> {
        val userContext = authUtils.extractUserContext(request)
            ?: throw UnauthorizedException("User authentication required")
        
        val updateRequest = UpdateTaskRequest(completed = false)
        val task = taskService.updateTask(userContext.userId, id, updateRequest)
            ?: throw TaskNotFoundException("Task not found")
        
        return ResponseEntity.ok(task)
    }
}