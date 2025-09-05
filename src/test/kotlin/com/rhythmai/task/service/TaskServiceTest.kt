package com.rhythmai.task.service

import com.rhythmai.task.model.*
import com.rhythmai.task.repository.TaskRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskServiceTest {
    
    private lateinit var taskService: TaskService
    private lateinit var taskRepository: TaskRepository
    private lateinit var analyticsService: AnalyticsService
    
    private val testUserId = "test-user-123"
    private val testTimezone = "America/New_York"
    
    @BeforeEach
    fun setUp() {
        taskRepository = mock()
        analyticsService = mock()
        taskService = TaskService(taskRepository, analyticsService)
    }
    
    @Test
    fun `getInboxTasks returns tasks without dueDate and projectId`() {
        // Given
        val pageable = PageRequest.of(0, 20)
        val inboxTask = Task(
            id = "inbox-1",
            userId = testUserId,
            title = "Inbox task",
            description = "Unorganized task",
            dueDate = null,
            projectId = null,
            completed = false,
            priority = Priority.MEDIUM,
            tags = emptyList(),
            position = 1000
        )
        
        val page = PageImpl(listOf(inboxTask), pageable, 1)
        
        whenever(taskRepository.findByUserIdAndDueDateIsNullAndProjectIdIsNullAndCompletedOrderByPositionAsc(
            testUserId, false, pageable
        )).thenReturn(page)
        
        // When
        val result = taskService.getInboxTasks(testUserId, false, pageable)
        
        // Then
        assertEquals(1, result.total)
        assertEquals(1, result.tasks.size)
        assertEquals("Inbox task", result.tasks[0].title)
        assertEquals(null, result.tasks[0].dueDate)
        assertEquals(null, result.tasks[0].projectId)
        
        // Verify analytics
        verify(analyticsService).trackWorkflowEvent(eq("inbox_viewed"), any())
    }
    
    @Test
    fun `getInboxTasks tracks inbox zero achievement when empty`() {
        // Given
        val pageable = PageRequest.of(0, 20)
        val emptyPage = PageImpl<Task>(emptyList(), pageable, 0)
        
        whenever(taskRepository.findByUserIdAndDueDateIsNullAndProjectIdIsNullAndCompletedOrderByPositionAsc(
            testUserId, false, pageable
        )).thenReturn(emptyPage)
        
        // When
        val result = taskService.getInboxTasks(testUserId, false, pageable)
        
        // Then
        assertEquals(0, result.total)
        assertTrue(result.tasks.isEmpty())
        
        // Verify inbox zero milestone tracked
        verify(analyticsService).trackUserJourneyMilestone(
            eq(testUserId),
            eq("inbox_zero_achieved"),
            any()
        )
    }
    
    @Test
    fun `getTodayTasks returns today and overdue tasks`() {
        // Given
        val pageable = PageRequest.of(0, 20)
        val zone = ZoneId.of(testTimezone)
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val todayEnd = todayStart.plus(1, ChronoUnit.DAYS)
        
        val todayTask = Task(
            id = "today-1",
            userId = testUserId,
            title = "Today's task",
            description = "Due today",
            dueDate = Instant.now(),
            projectId = "project-1",
            completed = false,
            priority = Priority.HIGH,
            tags = listOf("urgent"),
            position = 2000
        )
        
        val overdueTask = Task(
            id = "overdue-1",
            userId = testUserId,
            title = "Overdue task",
            description = "Was due yesterday",
            dueDate = Instant.now().minus(1, ChronoUnit.DAYS),
            projectId = null,
            completed = false,
            priority = Priority.HIGH,
            tags = listOf("overdue"),
            position = 1500
        )
        
        val page = PageImpl(listOf(overdueTask, todayTask), pageable, 2)
        
        whenever(taskRepository.findTodayTasks(testUserId, todayStart, todayEnd, false, pageable))
            .thenReturn(page)
        whenever(taskRepository.countByUserIdAndCompletedTrueAndCompletedAtBetween(
            testUserId, todayStart, todayEnd
        )).thenReturn(2)
        
        // When
        val result = taskService.getTodayTasks(testUserId, testTimezone, false, pageable)
        
        // Then
        assertEquals(2, result.total)
        assertEquals(2, result.tasks.size)
        
        // Verify analytics
        verify(analyticsService).trackWorkflowEvent(eq("today_view"), any())
        verify(analyticsService, never()).trackUserJourneyMilestone(any(), eq("productive_day"), any())
    }
    
    @Test
    fun `getTodayTasks tracks productive day milestone when 5+ tasks completed`() {
        // Given
        val pageable = PageRequest.of(0, 20)
        val zone = ZoneId.of(testTimezone)
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val todayEnd = todayStart.plus(1, ChronoUnit.DAYS)
        
        val page = PageImpl<Task>(emptyList(), pageable, 0)
        
        whenever(taskRepository.findTodayTasks(testUserId, todayStart, todayEnd, false, pageable))
            .thenReturn(page)
        whenever(taskRepository.countByUserIdAndCompletedTrueAndCompletedAtBetween(
            testUserId, todayStart, todayEnd
        )).thenReturn(5) // Productive day threshold
        
        // When
        taskService.getTodayTasks(testUserId, testTimezone, false, pageable)
        
        // Then
        verify(analyticsService).trackUserJourneyMilestone(
            eq(testUserId),
            eq("productive_day"),
            argThat { this["tasks_completed"] == 5L }
        )
    }
    
    @Test
    fun `getUpcomingTasks returns future tasks sorted by date and position`() {
        // Given
        val pageable = PageRequest.of(0, 20)
        val zone = ZoneId.of(testTimezone)
        val tomorrowStart = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()
        
        val tomorrowTask = Task(
            id = "tomorrow-1",
            userId = testUserId,
            title = "Tomorrow's task",
            description = "Due tomorrow",
            dueDate = Instant.now().plus(1, ChronoUnit.DAYS),
            projectId = "project-1",
            completed = false,
            priority = Priority.MEDIUM,
            tags = emptyList(),
            position = 3000
        )
        
        val nextWeekTask = Task(
            id = "nextweek-1",
            userId = testUserId,
            title = "Next week's task",
            description = "Due next week",
            dueDate = Instant.now().plus(7, ChronoUnit.DAYS),
            projectId = null,
            completed = false,
            priority = Priority.LOW,
            tags = listOf("future"),
            position = 4000
        )
        
        val page = PageImpl(listOf(tomorrowTask, nextWeekTask), pageable, 2)
        
        whenever(taskRepository.findByUserIdAndDueDateGreaterThanEqualAndCompletedOrderByDueDateAscPositionAsc(
            testUserId, tomorrowStart, false, pageable
        )).thenReturn(page)
        
        // When
        val result = taskService.getUpcomingTasks(testUserId, testTimezone, false, pageable)
        
        // Then
        assertEquals(2, result.total)
        assertEquals(2, result.tasks.size)
        assertEquals("Tomorrow's task", result.tasks[0].title)
        assertEquals("Next week's task", result.tasks[1].title)
        
        // Verify analytics
        verify(analyticsService).trackWorkflowEvent(eq("upcoming_view"), any())
    }
    
    @Test
    fun `trackViewContext tracks feature usage`() {
        // When
        taskService.trackViewContext(testUserId, "inbox")
        
        // Then
        verify(analyticsService).trackFeatureUsage(
            eq(testUserId),
            eq("view_navigation"),
            argThat { this["view"] == "inbox" }
        )
    }
    
    @Test
    fun `createTask with inbox context tracks inbox creation`() {
        // Given
        val request = CreateTaskRequest(
            title = "Inbox task",
            description = null,
            priority = Priority.MEDIUM,
            dueDate = null,
            projectId = null,
            tags = emptyList()
        )
        
        val createdTask = Task(
            id = "new-1",
            userId = testUserId,
            title = "Inbox task",
            description = null,
            dueDate = null,
            projectId = null,
            completed = false,
            priority = Priority.MEDIUM,
            tags = emptyList(),
            position = 5000
        )
        
        whenever(taskRepository.save(any())).thenReturn(createdTask)
        whenever(taskRepository.countByUserId(testUserId)).thenReturn(1)
        
        // When
        val result = taskService.createTask(testUserId, request, "inbox")
        
        // Then
        assertEquals("Inbox task", result.title)
        assertEquals(null, result.dueDate)
        assertEquals(null, result.projectId)
        
        // Verify context tracking
        verify(analyticsService).trackWorkflowEvent(
            eq("task_created_in_context"),
            argThat { this["context"] == "inbox" }
        )
        
        // Verify inbox milestone
        verify(analyticsService).trackUserJourneyMilestone(
            eq(testUserId),
            eq("inbox_task_created"),
            any()
        )
    }
}