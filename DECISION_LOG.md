# Task Service Decision Log

## December 2024 - Cross-Date Task Movement Enhancement

### Context
Enhanced the move API (`PATCH /api/tasks/{id}/move`) to support optional date changes during task repositioning. This allows tasks to be moved to different dates while maintaining positioning control.

### Decision
Added optional `targetDate` field to `MoveTaskRequest` DTO that accepts a `DueByRequest` object containing date/time information.

### Implementation Details

#### API Contract
```kotlin
data class MoveTaskRequest(
    val insertAfter: String? = null,
    val insertBefore: String? = null,
    val moveToTop: Boolean = false,
    val moveToBottom: Boolean = false,
    val targetDate: DueByRequest? = null  // NEW: Optional target date
)
```

#### Behavior
- **Without targetDate**: Task moves within its current date context (backward compatible)
- **With targetDate**: Task updates both date and position atomically
- **Reference validation**: When using `insertAfter`/`insertBefore`, validates reference tasks exist in the target context
- **Position strategies**: All positioning strategies (moveToTop, moveToBottom, insertAfter, insertBefore) work with date changes

### Validation & Guard Conditions

The implementation includes comprehensive validation to ensure data integrity:

1. **Reference Task Validation**:
   - Checks that reference task exists (404 if not found)
   - Verifies reference task belongs to same user (403 if different user)
   - **Critical**: Validates reference task is in the target date context (400 if mismatched)

2. **Error Responses**:
   - `NOT_FOUND (404)`: "Reference task not found: {task-id}"
   - `UNAUTHORIZED (403)`: "Reference task belongs to different user"
   - `INVALID_ARGUMENT (400)`: "Reference task is not in the target date context"

3. **Context Matching Logic**:
   ```kotlin
   // Verify reference task is in target context
   if (getDateContext(referenceTask.dueBy) != targetContext) {
       throw IllegalArgumentException("Reference task is not in the target date context")
   }
   ```

### Testing Results
✅ Cross-date movement with targetDate
✅ Same-date movement without targetDate (backward compatibility)
✅ All positioning strategies work with both modes
✅ Analytics properly tracks date changes
✅ Negative test: Prevents positioning relative to tasks on different dates
✅ Negative test: Clear error messages for validation failures

### Example Usage

#### Moving task to different date
```bash
curl -X PATCH http://localhost:5002/api/tasks/{id}/move \
  -H "Content-Type: application/json" \
  -H "X-User-ID: test-user" \
  -d '{
    "moveToTop": true,
    "targetDate": {
      "date": "2025-09-15",
      "timeType": "FIXED"
    }
  }'
```

#### Moving within same date (backward compatible)
```bash
curl -X PATCH http://localhost:5002/api/tasks/{id}/move \
  -H "Content-Type: application/json" \
  -H "X-User-ID: test-user" \
  -d '{
    "insertAfter": "other-task-id"
  }'
```

### Rationale
- **User need**: Drag-and-drop interfaces often need to move tasks between different date sections
- **Atomic operation**: Prevents race conditions by updating date and position together
- **Backward compatibility**: Existing clients continue to work without modification
- **Consistent API**: Uses same positioning strategies across all contexts

---

## December 2024 - Task Positioning Implementation

### Context
Implemented task reordering system to allow users to manually arrange tasks within their task lists. Position management is scoped by context (inbox or date-specific).

### Decision
Created dedicated `PATCH /api/tasks/{id}/move` endpoint with multiple positioning strategies rather than direct position manipulation.

### Implementation Details

#### Position System
- Integer positions with 1000-unit increments
- Separate position spaces per context (inbox, date-specific)
- Automatic position calculation based on strategy
- Future-ready for position rebalancing when gaps become too small

#### API Design
```kotlin
data class MoveTaskRequest(
    val insertAfter: String? = null,    // Position after specific task
    val insertBefore: String? = null,   // Position before specific task
    val moveToTop: Boolean = false,     // Move to top of context
    val moveToBottom: Boolean = false   // Move to bottom of context
)
```

### Rationale
- **Clear intent**: Each strategy explicitly describes the user's intention
- **Context-aware**: Automatically handles different task contexts
- **Conflict-free**: Midpoint calculation prevents position conflicts
- **RESTful**: PATCH semantics for partial updates

---

## December 2024 - View-Based Task Filtering

### Context
Implemented view-based filtering system to provide intuitive task organization without complex folder structures. Supports three primary views: Inbox, Today, and Upcoming.

### Decision
Used query parameter `view` with RESTful GET endpoint rather than separate endpoints for each view.

### Implementation Details

#### Views
1. **Inbox**: Tasks without due dates (`view=inbox`)
2. **Today**: Today's tasks + overdue tasks (`view=today`)
3. **Upcoming**: Future tasks excluding today (`view=upcoming`)

#### Technical Approach
- Single repository method with dynamic query building
- Timezone-aware date calculations using X-User-Timezone header
- Consistent sorting within each view (position-based for inbox/today, date-based for upcoming)

### Rationale
- **REST compliance**: Single resource endpoint with query filters
- **Flexibility**: Easy to add new views without API changes
- **Performance**: MongoDB query optimization per view
- **User experience**: Matches mental model from popular task apps

---

## November 2024 - Analytics Integration

### Context
Added comprehensive analytics tracking to understand user behavior and feature adoption.

### Decision
Embedded analytics directly in service layer rather than separate analytics service.

### Events Tracked
- Task lifecycle (create, complete, delete)
- View navigation patterns
- Feature usage (tags, projects, due dates)
- User milestones (inbox zero, productive days)

### Rationale
- **Simplicity**: No additional service complexity
- **Performance**: In-memory tracking with periodic persistence
- **Privacy**: All analytics stay within user context
- **Actionable**: Focused on improving user experience

---

## November 2024 - Initial Architecture

### Context
Building task management service as part of Havq personal work management system.

### Key Decisions

#### BFF Pattern
- Service not exposed directly to browser
- Trusts authentication headers from Express.js BFF
- No JWT validation in service layer

#### Technology Stack
- Spring Boot 3.2 + Kotlin 1.9
- MongoDB for persistence
- Gradle composite builds for shared library

#### Data Model
- Single `description` field with Markdown support
- UTC-only timestamps (timezone from headers)
- Position scoped by context, not global

### Rationale
- **Security**: Authentication centralized in BFF
- **Simplicity**: No duplicate auth logic
- **Performance**: Reduced JWT validation overhead
- **Maintainability**: Clear separation of concerns