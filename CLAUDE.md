# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**📋 For complete project context, see: `../rhythmai-docs/CLAUDE.md`**

## This Repository

- **Purpose**: Task management microservice for Rhythmai personal work management app
- **Tech Stack**: Spring Boot 3.2 + Kotlin 1.9 + Java 17
- **Port**: 5002 (internal service, not exposed to browser)
- **Status**: 🚧 Ready for implementation - currently empty repository

## Architecture Context

This service is part of Rhythmai's **BFF Gateway Pattern**:

```
Browser → Express.js BFF (port 3000) → Task Service (port 5002)
                                   → Project Service (port 5003)
                                   → Other Services...
```

### Key Architectural Points:

1. **Internal Service**: This service is NOT exposed to browsers - only accessible from Express.js BFF
2. **Authentication**: BFF handles OAuth2/JWT - this service trusts X-User-* headers from BFF
3. **Database**: Uses MongoDB with `rhythmai-tasks` collection
4. **Shared Library**: Uses `rhythmai-shared-kotlin` via Gradle Composite Build

## Expected Implementation

Based on requirements, this service will handle:

### Epic 2: Task Management Core
- Task CRUD operations (create, read, update, delete)
- Task completion/incompletion
- Due date management
- Priority levels (High/Medium/Low)
- Rich text descriptions with formatting
- Tag management and assignment
- Task search and filtering

### Technical Requirements:
- **Framework**: Spring Boot 3.2.0 + Kotlin 1.9.20
- **Build**: Gradle with Kotlin DSL
- **Database**: MongoDB (rhythmai-tasks collection)
- **Authentication**: Trust X-User-* headers from BFF (no JWT validation needed)
- **API**: REST endpoints under `/api/tasks`
- **Shared Code**: Use `rhythmai-shared-kotlin` for user context utilities

## Kotlin Service Structure (from Architecture Docs)

This service should follow the standard Rhythmai microservice structure:

```
src/main/kotlin/com/rhythmai/task/
├── controller/     # REST API endpoints (@RestController)
├── service/        # Business logic
├── repository/     # Data access (@Repository)
├── model/          # Data entities
├── config/         # Spring configuration
└── security/       # Auth & security logic (if needed)
```

### Package Organization:
- **controller**: REST controllers with `@RestController` annotation
- **service**: Business logic layer with `@Service` annotation
- **repository**: Data access with Spring Data MongoDB repositories
- **model**: Domain entities and DTOs
- **config**: Spring Boot configuration classes
- **security**: Authentication utilities (minimal - BFF handles auth)

## Shared Library Integration

This service uses `rhythmai-shared-kotlin` for common functionality:

### Gradle Composite Build Setup:

**settings.gradle.kts:**
```kotlin
rootProject.name = "rhythmai-task-service"

// Composite build - automatically rebuilds shared library when needed
includeBuild("../rhythmai-shared-kotlin")
```

**build.gradle.kts:**
```kotlin
dependencies {
    implementation("com.rhythmai:rhythmai-shared")
    // Other dependencies...
}
```

### Using Shared Components:

**Controller Example:**
```kotlin
@RestController
@RequestMapping("/api/tasks")
class TaskController(
    private val authUtils: AuthUtils,  // From shared library
    private val taskService: TaskService
) {
    @PostMapping
    fun createTask(
        request: HttpServletRequest,
        @RequestBody createTaskRequest: CreateTaskRequest
    ): TaskResponse {
        val user = authUtils.extractUserContext(request)  // Extract from BFF headers
        return taskService.createTask(user.userId, createTaskRequest)
    }
}
```

**Available Shared Components:**
- `UserContext` data class for user information
- `AuthUtils.extractUserContext()` for header extraction
- Common models and exception types

## Quick Start (When Implementing)

1. **Repository Setup**: 
   ```bash
   # Create Gradle project with Kotlin + Spring Boot
   gradle init --type kotlin-application
   ```

2. **Dependencies**: Spring Boot Web, MongoDB, Spring Boot Test
3. **Composite Build**: Add `../rhythmai-shared-kotlin` to `settings.gradle.kts`
4. **Configuration**: Spring profiles (local/staging/prod) with MongoDB URIs
5. **Controller**: REST endpoints that extract user from BFF headers

## Current State ✅ **MVP READY - HELLO WORLD ENDPOINT** (September 2025)

**🎯 Current Status**: Infrastructure complete, API endpoints commented out for clean development

### ✅ Completed Infrastructure:
- [x] **Complete Gradle Project Structure** with Kotlin DSL and Spring Boot 3.2
- [x] **Spring Boot Application Class** with auto-configuration
- [x] **MongoDB Configuration** with Spring profiles (local/staging/prod)
- [x] **REST Controllers** with comprehensive API endpoints (commented out)
- [x] **Service Layer** with full business logic implementation
- [x] **Data Models** (Task entity, DTOs, UserContext, Exceptions)
- [x] **Repository Layer** with Spring Data MongoDB and custom queries
- [x] **Authentication System** with BFF header extraction (AuthUtils)
- [x] **Global Exception Handling** with proper HTTP status codes
- [x] **Service Management Scripts** (start.sh, stop.sh, status.sh)
- [x] **Master Orchestration Integration** with ~/projects/ scripts
- [x] **Production Configuration** with Spring profiles and logging
- [x] **Health Monitoring** via Spring Actuator endpoints

### 🚀 **API Endpoints Status:**

**✅ Active & Tested:**
```
GET    /api/tasks/hello-world  # Hello World endpoint (working)
```

**📝 Ready but Commented Out (for clean development):**
```
POST   /api/tasks              # Create task
GET    /api/tasks              # List/search/filter tasks  
GET    /api/tasks/{id}         # Get specific task
PUT    /api/tasks/{id}         # Update task
DELETE /api/tasks/{id}         # Delete task
PATCH  /api/tasks/{id}/complete    # Mark complete
PATCH  /api/tasks/{id}/incomplete  # Mark incomplete
GET    /api/tasks/upcoming     # Get upcoming tasks
GET    /api/tasks/overdue      # Get overdue tasks
GET    /api/tasks/stats        # Get user statistics
```

### 📊 **Features Support:**
- **Task Management**: CRUD operations with priorities, due dates, tags
- **Search & Filtering**: By completion status, priority, tags, text search
- **Authentication**: X-User-* header extraction from BFF
- **Data Validation**: Request validation with meaningful error messages
- **Pagination**: Support for large task lists
- **Statistics**: Task completion metrics

### 🧪 **Testing Status:**
- ✅ **Build Success**: Gradle build completes without errors
- ✅ **Service Startup**: Starts successfully on port 5002
- ✅ **MongoDB Integration**: Successfully connects to local MongoDB
- ✅ **Hello World Endpoint**: `/api/tasks/hello-world` returns "hello world !!" 
- ✅ **Authentication**: Header-based auth infrastructure in place
- ✅ **Health Checks**: Spring Actuator endpoints operational
- ✅ **Orchestration**: Integrated with master start/stop/status scripts
- ✅ **Clean Swagger Documentation**: Shows only active endpoints

### 📚 **API Documentation (Swagger/OpenAPI):**
- **Framework**: SpringDoc OpenAPI 3.0.1 (version 2.2.0)
- **Interactive UI**: http://localhost:5002/swagger-ui.html
- **OpenAPI Spec**: http://localhost:5002/api-docs (simplified path)
- **Current Status**: Clean documentation showing only working hello-world endpoint
- **Next Steps**: Uncomment and test endpoints as they're implemented

## Key Design Principles

1. **Encouragement-First**: Use positive, supportive language in API responses
2. **Trust BFF Authentication**: No JWT validation - trust user headers
3. **MongoDB Integration**: Use Spring Data MongoDB for data access
4. **RESTful Design**: Follow standard REST patterns
5. **Error Handling**: Proper exception handling with meaningful responses

## Related Services

- **rhythmai-frontend**: Express.js BFF + React app (port 3000)
- **rhythmai-project-service**: Project management service (port 5003)
- **rhythmai-shared-kotlin**: Shared utilities and models
- **rhythmai-docs**: Central documentation and requirements

## Commands

```bash
# Service Management (Recommended)
./start.sh [local|staging|prod]     # Start service with profile (default: local)
./stop.sh                           # Stop service gracefully
./status.sh                         # Check service status and health

# Direct Gradle Commands
./gradlew bootRun                   # Start service on port 5002
./gradlew test                      # Run tests
./gradlew build                     # Build JAR
./gradlew bootRun --args="--spring.profiles.active=staging"  # With profile

# Master Orchestration (All Services)
~/projects/start.sh                 # Start MongoDB + Frontend BFF + Task Service
~/projects/stop.sh                  # Stop all services
~/projects/status.sh                # Check all service status

# API Documentation & Testing
http://localhost:5002/swagger-ui.html    # Interactive Swagger UI
http://localhost:5002/api-docs           # OpenAPI JSON specification
curl http://localhost:5002/api/tasks/hello-world  # Test hello world endpoint
```

## MongoDB Collections

This service manages:
- `rhythmai-tasks`: User tasks with full task management data

User data is managed by the BFF and shared via request headers.