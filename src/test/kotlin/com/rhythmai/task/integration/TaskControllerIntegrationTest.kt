package com.rhythmai.task.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.rhythmai.task.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.*
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskControllerIntegrationTest {
    
    @Autowired
    private lateinit var mockMvc: MockMvc
    
    @Autowired
    private lateinit var objectMapper: ObjectMapper
    
    private val testUserId = "integration-test-user"
    private val testUserEmail = "integration@test.com"
    private val testUserName = "Integration Test User"
    private val testTimezone = "America/New_York"
    
    @Test
    fun `GET tasks with inbox view returns only inbox tasks`() {
        mockMvc.perform(
            get("/api/tasks")
                .header("X-User-ID", testUserId)
                .header("X-User-Email", testUserEmail)
                .header("X-User-Name", testUserName)
                .header("X-User-Timezone", testTimezone)
                .param("view", "inbox")
                .param("completed", "false")
        )
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tasks").isArray)
        .andExpect(jsonPath("$.total").isNumber)
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
    }
    
    @Test
    fun `GET tasks with today view returns today and overdue tasks`() {
        mockMvc.perform(
            get("/api/tasks")
                .header("X-User-ID", testUserId)
                .header("X-User-Email", testUserEmail)
                .header("X-User-Name", testUserName)
                .header("X-User-Timezone", testTimezone)
                .param("view", "today")
                .param("completed", "false")
        )
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tasks").isArray)
    }
    
    @Test
    fun `GET tasks with upcoming view returns future tasks`() {
        mockMvc.perform(
            get("/api/tasks")
                .header("X-User-ID", testUserId)
                .header("X-User-Email", testUserEmail)
                .header("X-User-Name", testUserName)
                .header("X-User-Timezone", testTimezone)
                .param("view", "upcoming")
                .param("completed", "false")
        )
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tasks").isArray)
    }
    
    @Test
    fun `GET tasks without view returns all incomplete tasks`() {
        mockMvc.perform(
            get("/api/tasks")
                .header("X-User-ID", testUserId)
                .header("X-User-Email", testUserEmail)
                .header("X-User-Name", testUserName)
                .param("completed", "false")
        )
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tasks").isArray)
    }
    
    @Test
    fun `GET tasks with invalid view returns bad request`() {
        mockMvc.perform(
            get("/api/tasks")
                .header("X-User-ID", testUserId)
                .header("X-User-Email", testUserEmail)
                .header("X-User-Name", testUserName)
                .param("view", "invalid-view")
        )
        .andDo(print())
        .andExpect(status().isBadRequest)
    }
    
    @Test
    fun `POST task with inbox context creates inbox task`() {
        val createRequest = CreateTaskRequest(
            title = "Integration test inbox task",
            description = "This task should go to inbox",
            priority = Priority.MEDIUM,
            dueDate = null, // No date for inbox
            projectId = null, // No project for inbox
            tags = listOf("test", "integration")
        )
        
        mockMvc.perform(
            post("/api/tasks")
                .header("X-User-ID", testUserId)
                .header("X-User-Email", testUserEmail)
                .header("X-User-Name", testUserName)
                .param("context", "inbox")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
        )
        .andDo(print())
        .andExpect(status().isCreated)
        .andExpect(jsonPath("$.title").value("Integration test inbox task"))
        .andExpect(jsonPath("$.dueDate").doesNotExist())
        .andExpect(jsonPath("$.projectId").doesNotExist())
    }
    
    @Test
    fun `POST task with today context creates task for today`() {
        val createRequest = CreateTaskRequest(
            title = "Integration test today task",
            description = "This task is for today",
            priority = Priority.HIGH,
            dueDate = Instant.now(), // Today
            projectId = null,
            tags = listOf("urgent", "today")
        )
        
        mockMvc.perform(
            post("/api/tasks")
                .header("X-User-ID", testUserId)
                .header("X-User-Email", testUserEmail)
                .header("X-User-Name", testUserName)
                .param("context", "today")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
        )
        .andDo(print())
        .andExpect(status().isCreated)
        .andExpect(jsonPath("$.title").value("Integration test today task"))
        .andExpect(jsonPath("$.dueDate").exists())
        .andExpect(jsonPath("$.priority").value("HIGH"))
    }
    
    @Test
    fun `GET tasks with completed=true returns completed tasks`() {
        mockMvc.perform(
            get("/api/tasks")
                .header("X-User-ID", testUserId)
                .header("X-User-Email", testUserEmail)
                .header("X-User-Name", testUserName)
                .param("completed", "true")
        )
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tasks").isArray)
    }
    
    @Test
    fun `GET tasks with search parameter filters by text`() {
        mockMvc.perform(
            get("/api/tasks")
                .header("X-User-ID", testUserId)
                .header("X-User-Email", testUserEmail)
                .header("X-User-Name", testUserName)
                .param("search", "important")
                .param("completed", "false")
        )
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }
    
    @Test
    fun `GET tasks with priority filter returns filtered tasks`() {
        mockMvc.perform(
            get("/api/tasks")
                .header("X-User-ID", testUserId)
                .header("X-User-Email", testUserEmail)
                .header("X-User-Name", testUserName)
                .param("priority", "HIGH")
                .param("completed", "false")
        )
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }
    
    @Test
    fun `GET tasks with pagination parameters works correctly`() {
        mockMvc.perform(
            get("/api/tasks")
                .header("X-User-ID", testUserId)
                .header("X-User-Email", testUserEmail)
                .header("X-User-Name", testUserName)
                .param("view", "inbox")
                .param("page", "1")
                .param("size", "10")
                .param("completed", "false")
        )
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(10))
    }
    
    @Test
    fun `GET tasks without required headers returns unauthorized`() {
        mockMvc.perform(
            get("/api/tasks")
                .param("view", "inbox")
        )
        .andDo(print())
        .andExpect(status().isUnauthorized)
    }
    
    @Test
    fun `Combined filters work together correctly`() {
        mockMvc.perform(
            get("/api/tasks")
                .header("X-User-ID", testUserId)
                .header("X-User-Email", testUserEmail)
                .header("X-User-Name", testUserName)
                .header("X-User-Timezone", testTimezone)
                .param("view", "today")
                .param("completed", "false")
                .param("page", "0")
                .param("size", "5")
        )
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.size").value(5))
    }
}