# Task Service Decision Log

**Created**: September 4, 2025  
**Last Updated**: September 4, 2025  
**Purpose**: Document all architectural and design decisions for the Rhythmai Task Service

---

## Table of Contents
1. [Data Model Decisions](#data-model-decisions)
2. [Date & Time Handling](#date--time-handling)
3. [Description Field Strategy](#description-field-strategy)
4. [Position & Sorting Strategy](#position--sorting-strategy)
5. [API Design Decisions](#api-design-decisions)
6. [MongoDB Integration](#mongodb-integration)

---

## Data Model Decisions

### Decision 1: Core Task Fields
**Date**: September 4, 2025  
**Status**: ✅ Implemented  

**Decision**: Include the following core fields in the Task model:
- `id`: MongoDB ObjectId (auto-generated)
- `userId`: Task owner (from BFF headers)
- `projectId`: Optional project association
- `title`: Required task title
- `description`: Markdown-formatted content (single field)
- `completed`: Boolean completion status
- `priority`: Enum (LOW, MEDIUM, HIGH)
- `dueDate`: UTC Instant (optional)
- `tags`: List of strings for categorization
- `position`: Integer for manual ordering
- `createdAt`, `updatedAt`, `completedAt`: UTC timestamps

**Rationale**:
- Covers MVP requirements without over-engineering
- Supports drag-drop reordering via position field
- Project association ready for Epic 4
- Tags provide flexible categorization

**Deferred for Later**:
- AI insights field (can add later with no migration)
- Attachments (postponed to reduce complexity)
- Recurring patterns (future feature)
- Subtasks/parent relationships (keep flat for MVP)

---

## Date & Time Handling

### Decision 2: UTC-Only Storage
**Date**: September 4, 2025  
**Status**: ✅ Implemented  

**Decision**: Store all dates/times as UTC `Instant` in MongoDB

**Rejected Alternative**: Store both UTC and local date
- Would break when users travel or change timezones
- Creates data sync issues

**Implementation**:
```kotlin
val dueDate: Instant? = null  // UTC only
val createdAt: Instant = Instant.now()
```

**Rationale**:
- Single source of truth
- Travel-friendly (works across timezones)
- Frontend sends timezone in headers for conversion
- No data updates needed when user changes location

**Query Strategy**:
```kotlin
// Frontend sends: X-User-Timezone: America/New_York
fun getTasksDueToday(userId: String, timezone: String) {
    val zone = ZoneId.of(timezone)
    val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
    val todayEnd = todayStart.plus(1, ChronoUnit.DAYS)
    // Query between todayStart and todayEnd
}
```

---

## Description Field Strategy

### Decision 3: Single Description Field with Markdown
**Date**: September 4, 2025  
**Status**: ✅ Implemented  

**Decision**: Use single `description` field supporting Markdown formatting

**Rejected Alternative**: Dual fields (description + richDescription)
- Creates redundancy and confusion
- Unclear which field to use when
- Sync issues between plain and rich text

**Implementation**:
```kotlin
val description: String? = null  // Markdown content
// Removed: val richDescription: String?
```

**Frontend Strategy**:
- Render as Markdown in detail views
- Generate plain text preview for list views
- Single input field with Markdown preview

**Benefits**:
- Simpler mental model
- No data redundancy
- Frontend controls rendering

---

## Position & Sorting Strategy

### Decision 4: Position Field with Smart Defaults
**Date**: September 4, 2025  
**Status**: 🚧 Pending Implementation  

**Decision**: Position is optional with context-aware defaults

**Key Principles**:
1. **Position is scoped by date** (not global)
2. **Frontend can specify exact position or let backend calculate**
3. **Default insertion is context-dependent**
4. **New position assigned when task moves to different date**

**Implementation Strategy**:
```kotlin
data class CreateTaskRequest(
    // ... other fields ...
    val position: Int? = null,  // Optional
    val insertAfterTaskId: String? = null,  // Insert after specific task
    val insertAtTop: Boolean = false  // Insert at top of list
)
```

**Position Calculation**:
```kotlin
when {
    request.position != null -> use specified position
    request.insertAfterTaskId != null -> calculate midpoint
    request.insertAtTop -> minimum position - 1000
    else -> maximum position + 1000  // Default: append to end
}
```

**Position Scope**:
- Position 1000 on Sept 5 ≠ Position 1000 on Sept 6
- Moving task to new date = new position in that date's context
- Allows clean slate each day

**Gaps & Rebalancing**:
- Use 1000-point gaps initially
- Rebalance when gaps < 10
- Frontend handles drag-drop position updates

---

## API Design Decisions

### Decision 5: BFF Authentication Pattern
**Date**: September 4, 2025  
**Status**: ✅ Implemented  

**Decision**: Trust X-User-* headers from Express.js BFF

**Headers Required**:
- `X-User-ID`: User's unique identifier
- `X-User-Email`: User's email
- `X-User-Name`: User's display name

**Implementation**:
- No JWT validation in Task Service
- BFF handles all OAuth/JWT complexity
- Task Service extracts user context from headers
- Swagger UI updated to show header fields

**Rationale**:
- Simplifies microservice architecture
- Centralizes authentication in BFF
- Reduces JWT validation overhead
- Internal service not exposed to internet

### Decision 6: RESTful Endpoint Design
**Date**: September 4, 2025  
**Status**: ✅ Implemented  

**Active Endpoints**:
```
POST   /api/tasks           # Create task
GET    /api/tasks           # List with filters
GET    /api/tasks/hello-world  # Health check
```

**Pending Activation**:
```
GET    /api/tasks/{id}      # Get specific task
PUT    /api/tasks/{id}      # Update task
DELETE /api/tasks/{id}      # Delete task
PATCH  /api/tasks/{id}/complete    # Mark complete
PATCH  /api/tasks/{id}/incomplete  # Mark incomplete
```

---

## MongoDB Integration

### Decision 7: Spring Data MongoDB Repository Pattern
**Date**: September 4, 2025  
**Status**: ✅ Implemented  

**Decision**: Use Spring Data MongoDB with method naming conventions

**Key Patterns**:
1. **Method names become queries**: `findByUserIdOrderByCreatedAtDesc`
2. **Custom queries via @Query**: Complex searches with regex
3. **Auto-implementation**: Spring generates implementation at runtime
4. **Collection name**: `rhythmai-tasks`

**Query Logging**:
```yaml
logging:
  level:
    org.springframework.data.mongodb: DEBUG
```

**Benefits**:
- No manual query writing
- Type-safe repository methods
- Automatic query generation
- Visible in logs for debugging

---

---

## Position & Sorting Strategy

### Decision 8: Enhanced Position Calculation Logic
**Date**: September 5, 2025  
**Status**: ✅ Implemented  

**Decision**: Implement hybrid position control with 4-tier strategy

**Implementation**:
```kotlin
// 1. Frontend Precision Control
{ "position": 1500 }  // Exact position for drag-drop

// 2. Frontend Contextual Hints  
{ "insertAfterTaskId": "task-456", "position": null }

// 3. Backend Smart Defaults
{ "dueDate": "2025-09-06T10:00:00Z", "position": null }  // Append to date

// 4. Backend Bulk Operations
// Auto-calculated for imports and API clients
```

**Key Features**:
- **Date-scoped positioning**: Position resets for different dates
- **Smart insertion**: insertAtTop, insertAfterTaskId support
- **Cross-date moves**: Moving task to new date gets new position
- **Gap management**: 1000-point gaps with rebalancing when needed

**Test Results**: ✅ All scenarios working perfectly
- Default append: Position increments by 1000
- Insert at top: Position 0
- Different dates: Independent position sequences  
- Cross-date moves: New position in target date context

**Rationale**: 
- Supports both manual control and smart defaults
- Handles bulk imports and API clients gracefully
- User-friendly behavior matches mental models
- Scales for productivity-focused workflows

---

## Analytics & Feature Management

### Decision 9: Privacy-First Analytics with Plausible
**Date**: September 5, 2025  
**Status**: 🚧 Architecture Ready, Pending Implementation  

**Decision**: Integrate Plausible Analytics for privacy-first user insights

**Key Components**:
1. **Plausible Integration**: GDPR-compliant, no cookie banners ($9/month)
2. **Analytics Service**: Event tracking hooks in business logic
3. **Entitlements Service**: Future microservice for feature flags
4. **Privacy-First**: No PII tracking, aggregated usage only

**Implementation Strategy**:
```kotlin
interface AnalyticsService {
    fun trackTaskEvent(event: String, properties: Map<String, Any>)
}

// In TaskService
fun createTask(...): TaskResponse {
    val task = // business logic
    
    // Privacy-safe analytics 
    analyticsService.trackTaskEvent("task_created", mapOf(
        "priority" to task.priority.name,
        "has_due_date" to (task.dueDate != null),
        "has_project" to (task.projectId != null)
    ))
    
    return TaskResponse.from(task)
}
```

**Business Context**:
- Aligns with $438B productivity market focus
- Supports freemium model with usage insights
- Enables feature adoption tracking
- Privacy-compliant for international users

**Next Steps**:
1. Add AnalyticsService interface to Task Service
2. Implement event tracking hooks in CRUD operations
3. Design EntitlementsService for feature flags
4. Frontend integration with Plausible script

---

## Swagger UI Enhancement

### Decision 10: Authentication Headers in Swagger
**Date**: September 4, 2025  
**Status**: ✅ Implemented  

**Decision**: Add @RequestHeader annotations to expose BFF headers in Swagger UI

**Implementation**:
```kotlin
fun createTask(
    request: HttpServletRequest,
    @Parameter(description = "User ID from BFF", required = true, example = "test-user-123")
    @RequestHeader("X-User-ID") userId: String,
    @Parameter(description = "User email from BFF", required = true, example = "test@example.com")
    @RequestHeader("X-User-Email") userEmail: String,
    @Parameter(description = "User name from BFF", required = true, example = "Test User")
    @RequestHeader("X-User-Name") userName: String,
    // ... other parameters
): ResponseEntity<TaskResponse>
```

**Benefits**:
- Swagger UI now shows header fields as input forms
- Easy testing without curl commands
- Self-documenting authentication requirements
- Better developer experience

**Test Result**: ✅ Headers visible and functional in Swagger UI

---

## Pending Decisions

### To Be Discussed:
1. **Default Sort Order**: 
   - ✅ **RESOLVED**: Position-based sorting with date context
   - Default: Position ASC within date context

2. **Upcoming Tasks View**:
   - Separate endpoint with fixed sorting?
   - Part of main endpoint with filters?

3. **Position Rebalancing**:
   - When to trigger rebalancing?
   - Frontend or backend responsibility?

4. **Task Archival**:
   - Soft delete with recovery period?
   - Move to archive collection?

5. **EntitlementsService Design**:
   - Feature flag structure and storage?
   - Integration with billing system?
   - Caching strategy for performance?

### Decision 11: Performance Monitoring with @Timed Annotations
**Date**: September 5, 2025  
**Status**: ✅ Implemented  

**Decision**: Add method-level performance monitoring using Micrometer `@Timed` annotations

**Implementation**:
```kotlin
@Timed(value = "task.create", description = "Time taken to create a task")
fun createTask(userId: String, request: CreateTaskRequest): TaskResponse
```

**Key Components**:
- Spring Boot AOP dependency for timing aspects
- MetricsConfig with TimedAspect bean
- Method timing for: task.create, task.update, task.list, task.search
- Integration with Spring Boot Actuator metrics endpoint

**Benefits**:
- Production performance monitoring
- Method-level timing insights
- API response time tracking
- Performance regression detection

**Metrics Available**: http://localhost:5002/actuator/metrics/task.create

---

### Decision 12: Hybrid Analytics Architecture (Plausible + MongoDB)
**Date**: September 5, 2025  
**Status**: ✅ Implemented (Phase 1)  

**Decision**: Implement hybrid analytics approach - Plausible-first for MVP, with MongoDB hooks for future business intelligence

**Architecture Strategy**:
```
Phase 1: Plausible Analytics (Real-time insights)
- Product usage patterns
- Feature adoption rates
- Basic user journey tracking
- Cost: $9/month for 10K pageviews

Phase 2: MongoDB Analytics (Business intelligence)  
- Complex user journey analysis
- Churn prediction models
- Revenue correlation analysis
- Advanced ML/AI insights
```

**Implementation**:
- PlausibleReadyAnalyticsService with event formatting
- Privacy-safe user hashing (no PII)
- User journey milestone tracking
- Feature usage correlation analysis

**Key Events Tracked**:
1. **Product Analytics**: task_created, task_updated, search_performed
2. **User Journey**: first_task, first_completion, power_user_threshold
3. **Feature Usage**: due_dates, tags, projects, manual_positioning

**Business Intelligence Questions Addressed**:
- "How many tasks until users become sticky?"
- "Which features correlate with retention?"
- "What behaviors predict churn?"
- "What drives user engagement?"

**Cost Analysis**: 
- Pageviews (counted): User navigation between pages
- Custom events (unlimited): All our behavioral analytics
- Break-even: ~4 paying users ($80 revenue vs $73 costs)

---

### Decision 13: Privacy-First User Behavior Tracking
**Date**: September 5, 2025  
**Status**: ✅ Implemented  

**Decision**: Comprehensive user behavior tracking while maintaining strict privacy compliance

**Privacy Measures**:
- User ID hashing for anonymization
- No PII collection (names, emails, task content)
- GDPR-compliant event properties
- Aggregated usage patterns only

**Behavioral Patterns Tracked**:
```kotlin
// User journey milestones
analyticsService.trackUserJourneyMilestone(userId, "first_task", context)

// Feature adoption analysis  
analyticsService.trackFeatureUsage(userId, "due_dates", context)

// Engagement and productivity patterns
analyticsService.trackUserJourneyMilestone(userId, "productive_day", context)
```

**Business Value**:
- Understand productivity workflows
- Identify feature-retention correlations
- Enable data-driven product decisions
- Support freemium model optimization

**Compliance**: GDPR-ready, no cookie banners required

---

### Decision 14: View-Based Filtering with REST-Compliant Design
**Date**: December 2025  
**Status**: ✅ Implemented  

**Decision**: Implement view-based task filtering using optional query parameters following REST principles

**Key Design Choices**:
1. **Query Parameter Approach**: Use `?view=inbox|today|upcoming` as optional filter
2. **Default Behavior**: No view parameter returns all incomplete tasks (completed=false by default)
3. **REST Compliance**: Query parameters are optional, not mandatory
4. **Inbox Definition**: Tasks with no dueDate AND no projectId

**Implementation**:
```kotlin
// REST-compliant filtering
GET /api/tasks                    // All incomplete tasks (default)
GET /api/tasks?view=inbox         // Inbox view (no date, no project)
GET /api/tasks?view=today         // Today + overdue tasks
GET /api/tasks?view=upcoming      // Future tasks (tomorrow+)
GET /api/tasks?completed=true     // All completed tasks

// Context tracking for creation
POST /api/tasks?context=inbox     // Track where task was created
```

**View Definitions**:
- **Inbox**: `dueDate == null && projectId == null` (unorganized capture area)
- **Today**: Tasks due today + overdue tasks (needs attention)
- **Upcoming**: Tasks with `dueDate >= tomorrow` (future planning)
- **Default**: All tasks with `completed=false` (active work)

**Rationale**:
- Follows REST principles (optional query params)
- Maintains backwards compatibility
- Supports three primary UI views
- Clean separation of concerns
- Enables comprehensive analytics

**Analytics Integration**:
- Track view navigation patterns
- Monitor inbox zero achievements
- Detect productive day milestones
- Analyze task creation contexts

**Frontend Usage Pattern**:
```javascript
// Clear, explicit API calls
TaskAPI.inbox = () => fetch('/api/tasks?view=inbox&completed=false')
TaskAPI.today = () => fetch('/api/tasks?view=today&completed=false')
TaskAPI.upcoming = () => fetch('/api/tasks?view=upcoming&completed=false')
```

**Rejected Alternatives**:
1. ❌ Required context parameter - Violates REST principles
2. ❌ Separate endpoints (/api/tasks/inbox) - Not true resource separation
3. ❌ No default behavior - Poor developer experience

---

### Decision 15: Comprehensive Test Suite for View-Based Filtering
**Date**: December 5, 2025  
**Status**: ✅ Implemented  

**Decision**: Create comprehensive test coverage for view-based filtering functionality

**Test Coverage Implemented**:
1. **Unit Tests** (`TaskControllerTest.kt`):
   - View parameter validation (inbox, today, upcoming, invalid)
   - Default behavior testing (no view = incomplete tasks)
   - Context parameter tracking
   - Timezone handling (UTC fallback)
   - Error handling (400 for invalid view, 401 for auth)

2. **Integration Tests** (`TaskControllerIntegrationTest.kt`):
   - Full API endpoint testing with MockMvc
   - Header validation and security
   - Pagination and filter combinations
   - Real HTTP request/response cycles

3. **Service Layer Tests** (`TaskServiceTest.kt`):
   - Business logic for each view type
   - Analytics event tracking verification
   - Inbox zero milestone detection
   - Productive day milestone (5+ completions)

**Test Infrastructure**:
- Added Mockito-Kotlin, JUnit 5 dependencies
- Created `application-test.yml` configuration
- Embedded MongoDB for integration tests
- Background test execution capability

**Key Test Scenarios**:
- View filtering logic correctness
- Analytics integration verification
- Edge cases (empty results, timezone handling)
- Error conditions and proper HTTP status codes

---

### Decision 16: Enhanced Swagger/OpenAPI Documentation
**Date**: December 5, 2025  
**Status**: ✅ Implemented  

**Decision**: Provide comprehensive API documentation with detailed view behavior descriptions

**Documentation Enhancements**:
```yaml
GET /api/tasks:
  description: |
    **View Parameter Behavior:**
    - inbox: Returns unorganized tasks (no due date AND no project)
    - today: Returns tasks due today PLUS any overdue tasks
    - upcoming: Returns future tasks (due date >= tomorrow)
    - null/omitted: Returns all incomplete tasks (default behavior)
    
    **Timezone Handling:**
    - Uses X-User-Timezone header to determine "today" boundaries
    - Falls back to UTC if timezone header not provided
    
    **Sorting:**
    - Inbox: Sorted by position
    - Today: Sorted by due date (overdue first) then position
    - Upcoming: Sorted by due date then position
```

**Interactive Features**:
- Swagger UI available at http://localhost:5002/swagger-ui.html
- All parameters documented with examples
- Response codes and error conditions
- Authentication headers visible as input fields

---

### Decision 17: Timezone Handling Strategy - Fixed Time First, Floating Later
**Date**: December 6, 2025  
**Status**: 📋 Planned  

**Decision**: Start with fixed-time only implementation, design for future floating time support

**Context**: After analyzing Todoist and Asana data models, we discovered two approaches to timezone handling:
- **Todoist**: Supports both floating (default) and fixed time, optimized for personal productivity
- **Asana**: Fixed time only, optimized for team collaboration
- **Rhythmai Use Case**: Personal productivity with occasional collaboration (projects with assignees)

**Implementation Strategy**:

**Phase 1: Date-Only Tasks (Current)**
```kotlin
data class Task(
    val dueDate: String? = null,     // "2025-09-05" ISO date string
    val dueTime: Instant? = null,    // null for date-only tasks
    val timezone: String? = null     // null for date-only tasks
)
```

**Phase 2: Fixed Time Support (Next)**
```kotlin
data class Task(
    val dueDate: String? = null,     // "2025-09-05" ISO date string  
    val dueTime: Instant? = null,    // UTC timestamp when time specified
    val timezone: String? = null,    // Original timezone for display ("America/New_York")
    val timeType: TimeType = TimeType.FIXED  // Always FIXED in Phase 2
)
```

**Phase 3: Floating Time Support (Future, if needed)**
```kotlin
enum class TimeType {
    FIXED,       // Absolute UTC time (meetings, deadlines)
    FLOATING,    // Local time that moves with user (routines, habits)
    CONTEXTUAL   // Smart default based on project/assignee presence
}

// Smart defaults based on context:
// - Personal task (no project/assignee) → FLOATING
// - Shared task (has project/assignee) → FIXED
```

**Key Insights from Research**:

1. **Todoist's Floating Time** (Source: https://www.todoist.com/help/articles/set-a-fixed-time-or-floating-time-for-a-task-YUYVp27q):
   - **Default = Floating**: Tasks stay at same local time when traveling
   - **Example**: "Morning workout at 7 AM" stays 7 AM whether in NYC or Tokyo
   - **Collaboration**: All users see task at their local 7 AM
   - **Use Case**: Personal routines, habits, self-care

2. **Fixed Time (Standard)**:
   - **Absolute moment**: Same UTC timestamp globally
   - **Example**: "Team meeting at 9 AM EST" = 2 PM GMT = 11 PM JST
   - **Collaboration**: Each user sees converted local time
   - **Use Case**: Meetings, coordinated deadlines

**Query Implementation for Mixed Time Types (Phase 3)**:
```javascript
// MongoDB query handling both floating and fixed times
db.tasks.find({
  "userId": userId,
  "$or": [
    // Floating tasks: compare against user's current local date
    {
      "timeType": "FLOATING",
      "dueDate": "2025-09-06"  // User's local date
    },
    // Fixed tasks: compare against UTC timestamp range
    {
      "timeType": "FIXED",
      "dueTime": {
        "$gte": todayStartUTC,
        "$lt": todayEndUTC
      }
    }
  ]
})
```

**Rationale for Phased Approach**:
- **Simplicity First**: Fixed time covers collaboration use case
- **Proven Path**: Most successful tools start without floating time
- **User Feedback**: Add floating time only if users request it
- **No Breaking Changes**: Data model supports both from the start

**Travel Scenario Example**:
```
User in New York → Travels to Tokyo

Fixed Time Task: "Client call at 2 PM EST"
- In NYC: Shows as 2 PM
- In Tokyo: Shows as 3 AM (next day)
- Behavior: Adjusts to maintain same global moment

Floating Time Task: "Morning meditation at 7 AM" (Phase 3)
- In NYC: Shows as 7 AM
- In Tokyo: Shows as 7 AM  
- Behavior: Stays at same local time
```

**Business Value**:
- **Phase 2**: Enables project collaboration and shared deadlines
- **Phase 3**: Superior UX for travelers and digital nomads
- **Competitive Advantage**: Few tools handle both modes well

**Decision**: Implement Phase 2 (Fixed Time) immediately, defer Phase 3 (Floating Time) until user demand justifies complexity.

---

### Decision 18: Complex DueBy Data Model
**Date**: December 6, 2025  
**Status**: ✅ Implemented  

**Decision**: Replace flat `dueDate: Instant?` with nested `dueBy: DueBy?` object containing all temporal information

**Context**: During task reordering implementation, we discovered the need for more sophisticated date handling to support both all-day and time-specific tasks while maintaining clean position management.

**Implementation**:
```kotlin
// Before (Flat Structure)
data class Task(
    val dueDate: Instant? = null  // Single field, UTC only
)

// After (Complex Structure)
data class Task(
    val dueBy: DueBy? = null  // Nested object with multiple temporal aspects
)

data class DueBy(
    val date: String,                    // ISO date "2025-09-05"
    val time: Instant? = null,          // UTC timestamp for time-specific
    val timeType: TimeType = TimeType.FIXED
)
```

**Benefits**:
- **Clean API**: All temporal data grouped logically
- **Extensible**: Easy to add timezone or floating time later
- **Query Friendly**: MongoDB nested field queries (`dueBy.date`)
- **Context Aware**: Date-string enables context-scoped positioning

**Migration**: Implemented with backward compatibility via request/response DTOs

---

### Decision 19: PATCH /api/tasks/{id}/move Endpoint Design  
**Date**: December 6, 2025  
**Status**: ✅ Implemented  

**Decision**: Implement dedicated move endpoint instead of generic update for task reordering

**Research**: Analyzed Todoist (bulk item_reorder) and Asana (insertAfter/insertBefore) approaches

**API Design**:
```http
PATCH /api/tasks/{id}/move
{
  "insertAfter": "task-id",    // Position after specific task
  "insertBefore": "task-id",   // Position before specific task
  "moveToTop": true,           // Move to top of context
  "moveToBottom": true         // Move to bottom of context
}
```

**Key Features**:
- **Single Strategy Validation**: Exactly one positioning method per request
- **Context Awareness**: Maintains inbox vs date-based separation
- **Error Prevention**: Cross-context moves rejected with clear messages
- **Future Ready**: Bulk reorder can be added later if needed

**Alternative Considered**: Bulk reorder endpoint (deferred until proven necessary)

---

### Decision 20: Position-Based API Sorting
**Date**: December 6, 2025  
**Status**: ✅ Implemented  

**Decision**: All API responses must be sorted by position (ascending) for consistent UI behavior

**Problem**: APIs were returning tasks in `createdAt` order, making position-based reordering ineffective

**Solution**: 
- Repository methods: `OrderByCreatedAtDesc` → `OrderByPositionAsc`
- Controller: `Sort.by("position")` applied to all Pageable objects
- Service layer: Updated to use position-sorted repository methods

**Impact**: All endpoints now return predictably ordered tasks suitable for drag-and-drop interfaces

---

### Decision 21: Context-Scoped Position Management
**Date**: December 6, 2025  
**Status**: ✅ Implemented  

**Decision**: Position values are scoped by context ("inbox" vs "date:YYYY-MM-DD"), allowing independent sequences

**Context Logic**:
```kotlin
private fun getDateContext(dueBy: DueBy?): String {
    return if (dueBy != null) {
        "date:${dueBy.date}"  // e.g., "date:2025-09-10"
    } else {
        "inbox"
    }
}
```

**Position Ranges**:
- Inbox: 1000, 2000, 3000...
- Date 2025-09-10: 1000, 2000, 3000... (independent sequence)
- Date 2025-09-15: 1000, 2000, 3000... (independent sequence)

**Benefits**:
- **Logical Grouping**: Tasks on different dates don't interfere
- **Clean Positions**: Each context starts fresh at 1000
- **Move Safety**: Prevents invalid cross-context operations
- **Scalable**: Supports thousands of tasks per date

---

### Decision 22: No Timezone Storage in DueBy
**Date**: December 6, 2025  
**Status**: ✅ Implemented  

**Decision**: Explicitly exclude timezone field from DueBy object, contrary to initial research suggesting timezone storage

**User Direction**: "ok we dont want timezone storage - just the complex DueBy"

**Rationale**:
- **Simplicity**: Avoids timezone complexity in Phase 1
- **UTC Storage**: Time-specific tasks stored as UTC Instant
- **Date-Only Focus**: All-day tasks use ISO date strings only
- **Future Ready**: Timezone can be added later if needed

**Final Schema**:
```kotlin
data class DueBy(
    val date: String,                    // "2025-09-05"
    val time: Instant? = null,          // UTC for time-specific
    val timeType: TimeType = TimeType.FIXED
    // NO timezone field
)
```

This decision prioritizes implementation speed while maintaining extensibility for future timezone support.

---

## Change Log

### December 6, 2025 (Task Reordering & Complex DueBy Session)
- **Added Decision 18**: Complex DueBy Data Model (✅ Implemented)
- **Added Decision 19**: PATCH /api/tasks/{id}/move Endpoint Design (✅ Implemented)
- **Added Decision 20**: Position-Based API Sorting (✅ Implemented)
- **Added Decision 21**: Context-Scoped Position Management (✅ Implemented)
- **Added Decision 22**: No Timezone Storage in DueBy (✅ Implemented)
- **Completed**: Task reordering system with 4 positioning strategies
- **Research**: Documented Todoist and Asana API approaches in reorder.md
- **Enhanced**: All API responses now consistently sorted by position
- **Validated**: Cross-context move prevention and error handling
- **Future Planning**: Bulk reorder endpoint deferred until needed

### December 5, 2025 (Implementation Session)
- **Added Decision 14**: View-Based Filtering with REST Compliance (✅ Implemented)
- **Added Decision 15**: Comprehensive Test Suite (✅ Implemented)  
- **Added Decision 16**: Enhanced Swagger Documentation (✅ Implemented)
- Fixed orchestration scripts (removed `set -e` from stop.sh)
- Implemented inbox/today/upcoming views with full analytics
- Added timezone support via X-User-Timezone header
- Created 3-tier test coverage: unit, integration, service
- Enhanced API documentation with detailed behavior descriptions
- Verified all endpoints work correctly via testing

### December 2025

### September 5, 2025 (Extended Session)
- **Added Decision 11**: Performance Monitoring with @Timed (✅ Implemented)
- **Added Decision 12**: Hybrid Analytics Architecture (✅ Phase 1 Complete)
- **Added Decision 13**: Privacy-First User Behavior Tracking (✅ Implemented)
- **Updated Decision 9**: Analytics implementation completed
- **Cost analysis completed**: $9/month Plausible + infrastructure costs
- **User journey analytics**: All 4 key business questions addressed
- **Production-ready**: Performance metrics + privacy-compliant analytics

### September 5, 2025 (Initial)
- Added Decision 8: Enhanced Position Calculation Logic (✅ Implemented)
- Added Decision 9: Privacy-First Analytics with Plausible (🚧 → ✅ Completed)  
- Added Decision 10: Swagger UI Authentication Headers (✅ Implemented)
- Updated pending decisions with resolved items
- Comprehensive testing of position logic completed
- Business context integration from flowdeck presentations

### September 4, 2025
- Initial decision log created
- Documented data model decisions  
- Captured UTC-only date handling
- Documented single description field decision
- Outlined position/sorting strategy
- Recorded API and MongoDB decisions

---

## References
- [Task Model Code](src/main/kotlin/com/rhythmai/task/model/Task.kt)
- [Repository Interface](src/main/kotlin/com/rhythmai/task/repository/TaskRepository.kt)
- [Main CLAUDE.md](CLAUDE.md)
- [Architecture Docs](../rhythmai-docs/architecture/architecture.md)