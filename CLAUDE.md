# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**📋 For complete project context, see: `../havq-docs/CLAUDE.md`**

## 🔄 Recent Rebranding (September 2025)
**Company renamed from Rhythmai to Havq**
- All package names changed: `com.rhythmai` → `com.havq`
- MongoDB collections: `rhythmai-tasks` → `havq-tasks`
- Service directories: `rhythmai-*` → `havq-*`
- Domain: `havq.ai` (not havq.work)
- Orchestration scripts updated in `~/projects/` folder (start.sh, stop.sh, status.sh)

## Essential Documentation to Read

**IMPORTANT**: Always read these documents for context before starting work:
1. **Architecture Documentation**: `~/projects/havq-docs/architecture/` - System design and technical decisions
2. **Requirements Documentation**: `~/projects/havq-docs/requirements/` - Business requirements and user stories  
3. **Business Context**: `~/projects/flowdeck/docs/presentations/` - Business strategy and product vision (legacy archive)

These documents provide critical context for understanding the system's purpose, design decisions, and business goals.

## This Repository

- **Purpose**: Task management microservice for Havq team collaboration platform
- **Tech Stack**: Spring Boot 3.2 + Kotlin 1.9 + Java 17
- **Port**: 5002 (internal service, not exposed to browser)
- **Status**: ✅ **PRODUCTION READY** - Complete implementation with comprehensive testing

## Architecture Context

This service is part of Havq's **BFF Gateway Pattern**:

```
Browser → Express.js BFF (port 3000) → Task Service (port 5002)
                                   → Project Service (port 5003)
                                   → Other Services...
```

### Key Architectural Points:

1. **Internal Service**: This service is NOT exposed to browsers - only accessible from Express.js BFF
2. **Authentication**: BFF handles OAuth2/JWT - this service trusts X-User-* headers from BFF
3. **Database**: Uses MongoDB with `havq-tasks` collection
4. **Shared Library**: Uses `havq-shared-kotlin` via Gradle Composite Build

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
- **Database**: MongoDB (havq-tasks collection)
- **Authentication**: Trust X-User-* headers from BFF (no JWT validation needed)
- **API**: REST endpoints under `/api/tasks`
- **Shared Code**: Use `havq-shared-kotlin` for user context utilities

## Kotlin Service Structure (from Architecture Docs)

This service should follow the standard Havq microservice structure:

```
src/main/kotlin/com/havq/task/
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

This service uses `havq-shared-kotlin` for common functionality:

### Gradle Composite Build Setup:

**settings.gradle.kts:**
```kotlin
rootProject.name = "havq-task-service"

// Composite build - automatically rebuilds shared library when needed
includeBuild("../havq-shared-kotlin")
```

**build.gradle.kts:**
```kotlin
dependencies {
    implementation("com.havq:havq-shared")
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
3. **Composite Build**: Add `../havq-shared-kotlin` to `settings.gradle.kts`
4. **Configuration**: Spring profiles (local/staging/prod) with MongoDB URIs
5. **Controller**: REST endpoints that extract user from BFF headers

## Current State ✅ **PRODUCTION READY - VIEW-BASED FILTERING** (December 2025)

**🎯 Current Status**: Complete implementation with view-based filtering, comprehensive testing, and enhanced documentation

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

**✅ Active & Production Ready:**
```
GET    /api/tasks/hello-world  # Hello World endpoint (working)
POST   /api/tasks              # Create task with context tracking
GET    /api/tasks              # List/search/filter with view-based filtering
GET    /api/tasks?view=inbox   # Inbox view (unorganized tasks)
GET    /api/tasks?view=today   # Today view (today + overdue tasks)  
GET    /api/tasks?view=upcoming # Upcoming view (future tasks)
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
- **View-Based Filtering**: Inbox/Today/Upcoming views with REST compliance
- **Task Management**: CRUD operations with priorities, due dates, tags
- **Search & Filtering**: By completion status, priority, tags, text search
- **Authentication**: X-User-* header extraction from BFF
- **Timezone Support**: X-User-Timezone header for date calculations
- **Analytics Integration**: Context tracking, milestones, feature usage
- **Data Validation**: Request validation with meaningful error messages
- **Pagination**: Support for large task lists
- **Statistics**: Task completion metrics

### 🧪 **Testing Status:**
- ✅ **Complete Test Suite**: Unit, integration, and service layer tests
- ✅ **View Filtering Tests**: All three views (inbox/today/upcoming) tested
- ✅ **Analytics Tests**: Event tracking and milestone verification
- ✅ **Error Handling Tests**: 400/401 status codes and edge cases
- ✅ **Build Success**: Gradle build completes without errors
- ✅ **Service Startup**: Starts successfully on port 5002
- ✅ **MongoDB Integration**: Successfully connects to local MongoDB
- ✅ **All API Endpoints**: Complete CRUD operations tested
- ✅ **Authentication**: Header-based auth infrastructure in place
- ✅ **Health Checks**: Spring Actuator endpoints operational
- ✅ **Orchestration**: Integrated with master start/stop/status scripts
- ✅ **Enhanced Swagger Documentation**: Comprehensive API documentation

### 📚 **API Documentation (Swagger/OpenAPI):**
- **Framework**: SpringDoc OpenAPI 3.0.1 (version 2.2.0)
- **Interactive UI**: http://localhost:5002/swagger-ui.html
- **OpenAPI Spec**: http://localhost:5002/api-docs (simplified path)
- **Current Status**: ✅ Complete documentation with detailed view behavior descriptions
- **Enhanced Features**: 
  - Detailed view parameter explanations (inbox/today/upcoming)
  - Timezone handling documentation
  - Sorting behavior for each view
  - Authentication header examples
  - Error response codes and scenarios

### 🔧 **Orchestration Scripts Status:**
- **~/projects/start.sh**: ✅ Starts MongoDB + Frontend + Task Service
- **~/projects/stop.sh**: ✅ Gracefully stops all services (fixed `set -e` issue)  
- **~/projects/status.sh**: ✅ Shows accurate status of all services
- **Integration**: ✅ All scripts work together seamlessly
- **Logging**: ✅ Comprehensive logs for debugging
- **PID Management**: ✅ Clean process tracking and termination

## Key Design Principles

1. **Encouragement-First**: Use positive, supportive language in API responses
2. **Trust BFF Authentication**: No JWT validation - trust user headers
3. **MongoDB Integration**: Use Spring Data MongoDB for data access
4. **RESTful Design**: Follow standard REST patterns
5. **Error Handling**: Proper exception handling with meaningful responses

## Development Approach

### 🤝 **Collaborative Discussion Mode**
**IMPORTANT**: Before making any significant changes to the codebase, especially data models, API contracts, or architectural decisions, please:
1. **Discuss thoroughly** with the user first
2. **Present design choices** with pros and cons of each option
3. **Seek confirmation** before implementing any changes
4. **Limit the amount of changes** - make focused, incremental updates rather than large sweeping modifications
5. **Explain the implications** of different choices
6. **NEVER commit changes** - Do NOT use git commit unless explicitly asked by the user

This discussion-first approach ensures better decision-making and helps avoid costly refactoring later. Always collaborate by presenting multiple design options, analyzing trade-offs, and waiting for explicit confirmation before proceeding with implementation.

## Related Services

- **havq-frontend**: Express.js BFF + React app (port 3000)
- **havq-project-service**: Project management service (port 5003)
- **havq-shared-kotlin**: Shared utilities and models
- **havq-docs**: Central documentation and requirements

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
- `havq-tasks`: User tasks with full task management data

User data is managed by the BFF and shared via request headers.

## 📋 Current State (December 2025)

**📖 For all design decisions and rationale, see: [DECISION_LOG.md](docs/DECISION_LOG.md)**

### 🎯 **Ready for Frontend Integration:**

**API Endpoints Ready:**
```bash
# View-based filtering (main feature)
GET /api/tasks?view=inbox&completed=false
GET /api/tasks?view=today&completed=false  
GET /api/tasks?view=upcoming&completed=false

# Task creation with context
POST /api/tasks?context=inbox

# Standard CRUD operations
GET /api/tasks/{id}
PUT /api/tasks/{id}
DELETE /api/tasks/{id}
PATCH /api/tasks/{id}/complete
PATCH /api/tasks/{id}/incomplete

# Task reordering and positioning
PATCH /api/tasks/{id}/move  # With optional targetDate for cross-date moves
```

**Frontend Integration Notes:**
1. **Headers Required**: X-User-ID, X-User-Email, X-User-Name from BFF
2. **Timezone**: Send X-User-Timezone for accurate "today" boundaries  
3. **Analytics**: Use context parameter for tracking user workflows
4. **Default Filters**: completed=false is default for active task views
5. **Completed Section**: `GET /api/tasks?completed=true` returns ONLY tasks completed today (not all completed tasks)
6. **Error Handling**: 400 for invalid views, 401 for missing auth

### 📊 **Recent Updates (December 2025):**

#### Completed Tasks Filtering (December 9, 2025)
- **Changed Behavior**: `GET /api/tasks?completed=true` now returns only tasks completed TODAY
- **Previous**: Returned all completed tasks ever (not useful for daily view)
- **Rationale**: Shows daily accomplishments in the completed section
- **Implementation**: Filters by `completedOn.date` matching today's date
- **Legacy Cleanup**: Removed all deprecated `completedAt` and `completedDate` fields

#### Cross-Date Task Movement (December 8, 2025)
- **Enhanced Move API**: `PATCH /api/tasks/{id}/move` now accepts optional `targetDate` field
- **Atomic Updates**: Changes both date and position in single operation
- **Smart Validation**: Ensures reference tasks (insertAfter/insertBefore) are in target date context
- **Backward Compatible**: Works without targetDate for same-date repositioning
- **Error Handling**: Clear validation messages for mismatched date contexts

#### CompletedOn Complex Type Implementation
- **New Structure**: Tasks now use `CompletedOn` complex type matching `DueBy` pattern
- **Backward Compatibility**: Legacy `completedAt` and `completedDate` fields auto-migrate on read
- **Migration Script**: `docs/migrate-completed-on.js` for one-time data migration
- **Semantic Consistency**: Both `DueBy` and `CompletedOn` follow same structure (date, time, timeType)

#### Today View Enhancement
- **Now Includes**: Tasks completed today (regardless of original due date)
- **Query Updates**: All MongoDB queries properly escaped with `'\$or'`, `'\$lt'`, etc.
- **Count Fix**: Added `count = true` to aggregation queries to handle empty results

### 📊 **Analytics Events Tracked:**
- View navigation (inbox/today/upcoming usage)
- Task creation context (which view user was in)
- Inbox zero achievements  
- Productive day milestones (5+ completions)
- Feature usage (due dates, tags, projects, positioning)

### ✅ **Documentation Organization:**
All technical documentation has been organized in the `docs/` folder:
- `docs/DECISION_LOG.md` - All technical decisions and rationale
- `docs/ASANA_DATA_MODEL_STUDY.md` - Asana data model research
- `docs/TODOIST_DATA_MODEL_STUDY.md` - Todoist data model research
- `docs/reorder.md` - Task reordering implementation details
- `docs/migrate-completed-on.js` - MongoDB migration script for CompletedOn

### Quick Reference - Task Model
- **Single `description` field** supporting Markdown (no richDescription)
- **UTC-only dates** as `Instant` (timezone from frontend headers)
- **Position scoped by date** (not global, resets when moving dates)
- **Active endpoints**: POST /api/tasks, GET /api/tasks
- **Swagger UI**: Headers now visible as input fields

## 📝 Recent Session Work (September 10, 2025)

### Completed Today:
- ✅ **Service Testing**: Verified all services are operational (MongoDB, Task Service, Frontend BFF)
- ✅ **Database Integration**: Connected all 15 tasks to authenticated user (`68c1006c0994d96f7fe352a9`)
- ✅ **Data Cleanup**: Updated task ownership to match user in havq-auth-local database  
- ✅ **Legacy Rebranding**: Complete rebranding of flowdeck repository from Rhythmai to Havq
- ✅ **Documentation Updates**: Updated flowdeck CLAUDE.md with legacy status and current state

### Current System Status:
- **Task Service**: ✅ Running on port 5002, all endpoints operational
- **MongoDB**: ✅ Connected with 15 tasks linked to authenticated user
- **API Testing**: ✅ All CRUD operations working (hello-world, task filtering, creation)
- **User Context**: ✅ Proper X-User-* header extraction and authentication
- **View Filtering**: ✅ Today (5 tasks), Upcoming (10 tasks), Inbox (0 tasks) working

### Database State:
- **havq-tasks-local**: 15 tasks properly linked to user `68c1006c0994d96f7fe352a9`
- **havq-auth-local**: User Tanambam Debasis Sinha (tdebasis@gmail.com) authenticated
- **Task Distribution**: Today view (5), Upcoming view (10), all incomplete tasks

### Next Implementation Priority
1. ✅ Position-based sorting in repository (implemented)
2. ✅ Sort parameters for GET endpoint (implemented)  
3. ✅ BFF proxy configuration (completed)
4. **New Priority**: Consider Epic 3 (Production Deployment) or Epic 2.6 (Landing Page)

### Key Architectural Notes for Next Session:
- Service fully operational with MongoDB integration
- All documentation rebranded and up-to-date
- Ready for frontend integration or production deployment planning
- No blocking issues - system is production-ready