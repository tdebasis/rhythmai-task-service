package com.rhythmai.task.controller

import com.rhythmai.task.model.*
import com.rhythmai.task.security.AuthUtils
import com.rhythmai.task.service.TaskService
import com.rhythmai.task.service.TaskStats
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.temporal.ChronoUnit
import jakarta.servlet.http.HttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TaskControllerTest {
    
    private lateinit var taskController: TaskController
    private lateinit var taskService: TaskService
    private lateinit var authUtils: AuthUtils
    private lateinit var request: HttpServletRequest
    
    private val testUserId = "test-user-123"
    private val testUserEmail = "test@example.com"
    private val testUserName = "Test User"
    private val testTimezone = "America/New_York"
    
    @BeforeEach
    fun setUp() {
        taskService = mock()
        authUtils = mock()
        request = mock()
        taskController = TaskController(taskService, authUtils)
        
        // Setup default auth behavior
        val userContext = UserContext(
            userId = testUserId,
            email = testUserEmail,
            name = testUserName
        )
        whenever(authUtils.extractUserContext(request)).thenReturn(userContext)
    }
    
    @Test
    fun `getAllTasks with inbox view returns inbox tasks`() {
        // Given
        val pageable = PageRequest.of(0, 20)
        val expectedResponse = TaskListResponse(
            tasks = listOf(
                TaskResponse(
                    id = "1",
                    title = "Inbox task",
                    description = null,
                    dueDate = null,
                    projectId = null,
                    completed = false,
                    priority = Priority.MEDIUM,
                    tags = emptyList(),
                    position = 1000,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    completedAt = null
                )
            ),
            total = 1,
            page = 0,
            size = 20
        )
        
        whenever(taskService.getInboxTasks(testUserId, false, pageable))
            .thenReturn(expectedResponse)
        
        // When
        val response = taskController.getAllTasks(
            request = request,
            userId = testUserId,
            userEmail = testUserEmail,
            userName = testUserName,
            userTimezone = testTimezone,
            view = "inbox",
            page = 0,
            size = 20,
            completed = false,
            priority = null,
            tag = null,
            search = null
        )
        
        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(expectedResponse, response.body)
        verify(taskService).trackViewContext(testUserId, "inbox")
        verify(taskService).getInboxTasks(testUserId, false, pageable)
    }
    
    @Test
    fun `getAllTasks with today view returns today tasks`() {
        // Given
        val pageable = PageRequest.of(0, 20)
        val todayTask = TaskResponse(
            id = "2",
            title = "Today's task",
            description = "Due today",
            dueDate = Instant.now(),
            projectId = null,
            completed = false,
            priority = Priority.HIGH,
            tags = listOf("urgent"),
            position = 2000,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            completedAt = null
        )
        
        val expectedResponse = TaskListResponse(
            tasks = listOf(todayTask),
            total = 1,
            page = 0,
            size = 20
        )
        
        whenever(taskService.getTodayTasks(testUserId, testTimezone, false, pageable))
            .thenReturn(expectedResponse)
        
        // When
        val response = taskController.getAllTasks(
            request = request,
            userId = testUserId,
            userEmail = testUserEmail,
            userName = testUserName,
            userTimezone = testTimezone,
            view = "today",
            page = 0,
            size = 20,
            completed = false,
            priority = null,
            tag = null,
            search = null
        )
        
        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(expectedResponse, response.body)
        verify(taskService).trackViewContext(testUserId, "today")
        verify(taskService).getTodayTasks(testUserId, testTimezone, false, pageable)
    }
    
    @Test
    fun `getAllTasks with upcoming view returns upcoming tasks`() {
        // Given
        val pageable = PageRequest.of(0, 20)
        val upcomingTask = TaskResponse(
            id = "3",
            title = "Future task",
            description = "Due tomorrow",
            dueDate = Instant.now().plus(1, ChronoUnit.DAYS),
            projectId = "project-1",
            completed = false,
            priority = Priority.LOW,
            tags = emptyList(),
            position = 3000,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            completedAt = null
        )
        
        val expectedResponse = TaskListResponse(
            tasks = listOf(upcomingTask),
            total = 1,
            page = 0,
            size = 20
        )
        
        whenever(taskService.getUpcomingTasks(testUserId, testTimezone, false, pageable))
            .thenReturn(expectedResponse)
        
        // When
        val response = taskController.getAllTasks(
            request = request,
            userId = testUserId,
            userEmail = testUserEmail,
            userName = testUserName,
            userTimezone = testTimezone,
            view = "upcoming",
            page = 0,
            size = 20,
            completed = false,
            priority = null,
            tag = null,
            search = null
        )
        
        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(expectedResponse, response.body)
        verify(taskService).trackViewContext(testUserId, "upcoming")
        verify(taskService).getUpcomingTasks(testUserId, testTimezone, false, pageable)
    }
    
    @Test
    fun `getAllTasks without view parameter returns all incomplete tasks`() {
        // Given
        val pageable = PageRequest.of(0, 20)
        val expectedResponse = TaskListResponse(
            tasks = listOf(
                TaskResponse(
                    id = "4",
                    title = "Any task",
                    description = "Regular task",
                    dueDate = Instant.now(),
                    projectId = "project-1",
                    completed = false,
                    priority = Priority.MEDIUM,
                    tags = listOf("work"),
                    position = 4000,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    completedAt = null
                )
            ),
            total = 1,
            page = 0,
            size = 20
        )
        
        whenever(taskService.getTasksByCompleted(testUserId, false, pageable))
            .thenReturn(expectedResponse)
        
        // When
        val response = taskController.getAllTasks(
            request = request,
            userId = testUserId,
            userEmail = testUserEmail,
            userName = testUserName,
            userTimezone = null, // No timezone provided
            view = null, // No view specified
            page = 0,
            size = 20,
            completed = false, // Defaults to false
            priority = null,
            tag = null,
            search = null
        )
        
        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(expectedResponse, response.body)
        verify(taskService, never()).trackViewContext(any(), any())
        verify(taskService).getTasksByCompleted(testUserId, false, pageable)
    }
    
    @Test
    fun `getAllTasks with invalid view parameter throws BadRequestException`() {
        // When/Then
        assertThrows<BadRequestException> {
            taskController.getAllTasks(
                request = request,
                userId = testUserId,
                userEmail = testUserEmail,
                userName = testUserName,
                userTimezone = testTimezone,
                view = "invalid-view",
                page = 0,
                size = 20,
                completed = false,
                priority = null,
                tag = null,
                search = null
            )
        }
    }
    
    @Test
    fun `getAllTasks uses UTC timezone when not provided`() {
        // Given
        val pageable = PageRequest.of(0, 20)
        val expectedResponse = TaskListResponse(tasks = emptyList(), total = 0, page = 0, size = 20)
        
        whenever(taskService.getTodayTasks(testUserId, "UTC", false, pageable))
            .thenReturn(expectedResponse)
        
        // When
        val response = taskController.getAllTasks(
            request = request,
            userId = testUserId,
            userEmail = testUserEmail,
            userName = testUserName,
            userTimezone = null, // No timezone provided
            view = "today",
            page = 0,
            size = 20,
            completed = false,
            priority = null,
            tag = null,
            search = null
        )
        
        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        verify(taskService).getTodayTasks(testUserId, "UTC", false, pageable)
    }
    
    @Test
    fun `createTask with context tracks creation context`() {
        // Given
        val createRequest = CreateTaskRequest(
            title = "New inbox task",
            description = null,
            priority = Priority.MEDIUM,
            dueDate = null,
            projectId = null,
            tags = emptyList()
        )
        
        val createdTask = TaskResponse(
            id = "5",
            title = "New inbox task",
            description = null,
            dueDate = null,
            projectId = null,
            completed = false,
            priority = Priority.MEDIUM,
            tags = emptyList(),
            position = 5000,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            completedAt = null
        )
        
        whenever(authUtils.validateUserContext(any())).thenReturn(true)
        whenever(taskService.createTask(testUserId, createRequest, "inbox"))
            .thenReturn(createdTask)
        
        // When
        val response = taskController.createTask(
            request = request,
            userId = testUserId,
            userEmail = testUserEmail,
            userName = testUserName,
            context = "inbox",
            createRequest = createRequest
        )
        
        // Then
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals(createdTask, response.body)
        verify(taskService).createTask(testUserId, createRequest, "inbox")
    }
    
    @Test
    fun `getAllTasks with completed true returns completed tasks`() {
        // Given
        val pageable = PageRequest.of(0, 20)
        val completedTask = TaskResponse(
            id = "6",
            title = "Completed task",
            description = "Already done",
            dueDate = Instant.now().minus(1, ChronoUnit.DAYS),
            projectId = null,
            completed = true,
            priority = Priority.HIGH,
            tags = listOf("done"),
            position = 6000,
            createdAt = Instant.now().minus(2, ChronoUnit.DAYS),
            updatedAt = Instant.now(),
            completedAt = Instant.now()
        )
        
        val expectedResponse = TaskListResponse(
            tasks = listOf(completedTask),
            total = 1,
            page = 0,
            size = 20
        )
        
        whenever(taskService.getTodayTasks(testUserId, testTimezone, true, pageable))
            .thenReturn(expectedResponse)
        
        // When
        val response = taskController.getAllTasks(
            request = request,
            userId = testUserId,
            userEmail = testUserEmail,
            userName = testUserName,
            userTimezone = testTimezone,
            view = "today",
            page = 0,
            size = 20,
            completed = true, // Looking for completed tasks
            priority = null,
            tag = null,
            search = null
        )
        
        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(expectedResponse, response.body)
        verify(taskService).getTodayTasks(testUserId, testTimezone, true, pageable)
    }
}