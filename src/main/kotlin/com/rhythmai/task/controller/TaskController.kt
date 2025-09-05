package com.rhythmai.task.controller

import com.rhythmai.task.model.*
import com.rhythmai.task.security.AuthUtils
import com.rhythmai.task.service.TaskService
import com.rhythmai.task.service.TaskStats
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.Duration

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Task management operations")
class TaskController(
    private val taskService: TaskService,
    private val authUtils: AuthUtils
) {
    
    @GetMapping("/hello-world")
    @Operation(summary = "Hello World", description = "Simple test endpoint to verify API connectivity")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Successful response",
            content = [Content(mediaType = "application/json",
                schema = Schema(example = "{\"message\": \"hello world !!\"}"))])
    ])
    fun helloWorld(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("message" to "hello world !!"))
    }
    
    @PostMapping
    @Operation(summary = "Create Task", description = "Create a new task for the authenticated user")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Task created successfully",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = TaskResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request data"),
        ApiResponse(responseCode = "401", description = "Authentication required")
    ])
    fun createTask(
        request: HttpServletRequest,
        @Parameter(description = "User ID from BFF", required = true, example = "test-user-123")
        @RequestHeader("X-User-ID") userId: String,
        @Parameter(description = "User email from BFF", required = true, example = "test@example.com")
        @RequestHeader("X-User-Email") userEmail: String,
        @Parameter(description = "User name from BFF", required = true, example = "Test User")
        @RequestHeader("X-User-Name") userName: String,
        @Valid @RequestBody 
        @Parameter(description = "Task creation request") createRequest: CreateTaskRequest
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
    @Operation(summary = "Get Tasks", description = "Retrieve tasks with optional filtering and pagination")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Tasks retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Authentication required")
    ])
    fun getAllTasks(
        request: HttpServletRequest,
        @Parameter(description = "User ID from BFF", required = true, example = "test-user-123")
        @RequestHeader("X-User-ID") userId: String,
        @Parameter(description = "User email from BFF", required = true, example = "test@example.com")
        @RequestHeader("X-User-Email") userEmail: String,
        @Parameter(description = "User name from BFF", required = true, example = "Test User")
        @RequestHeader("X-User-Name") userName: String,
        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "Filter by completion status") @RequestParam(required = false) completed: Boolean?,
        @Parameter(description = "Filter by priority level") @RequestParam(required = false) priority: Priority?,
        @Parameter(description = "Filter by tag") @RequestParam(required = false) tag: String?,
        @Parameter(description = "Search in title and description") @RequestParam(required = false) search: String?
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
        @Parameter(description = "User ID from BFF", required = true, example = "test-user-123")
        @RequestHeader("X-User-ID") userId: String,
        @Parameter(description = "User email from BFF", required = true, example = "test@example.com")
        @RequestHeader("X-User-Email") userEmail: String,
        @Parameter(description = "User name from BFF", required = true, example = "Test User")
        @RequestHeader("X-User-Name") userName: String,
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
        val now = Instant.now()
        val end = now.plus(Duration.ofDays(daysAhead.toLong()))
        
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