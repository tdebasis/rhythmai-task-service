# Task Reordering Implementation Research

## Research Date: December 2024

## Competitive Analysis

### Todoist Approach

#### REST API (v2)
- Simple `order` field (integer) on task create/update operations
- Individual task updates for position changes
- Read-only when retrieving task details

#### Sync API (v9)
- Bulk reordering with dedicated `item_reorder` command
- Can update multiple tasks in single API call
- Example command:
```json
{
  "type": "item_reorder",
  "uuid": "unique-id",
  "args": {
    "project_id": "project_id",
    "items": [
      {"id": "task_id_1", "child_order": 1},
      {"id": "task_id_2", "child_order": 2},
      {"id": "task_id_3", "child_order": 3}
    ]
  }
}
```

**Key Insights:**
- Uses `child_order` for positioning within parent context
- Lower numbers = higher position in list
- Supports bulk operations for efficiency
- Context-aware (project/section specific)

### Asana Approach

- Uses `insertAfter` and `insertBefore` parameters
- Reordering happens during task movement operations (addProject API)
- Example for subtasks:
```
POST /tasks/{task-id}/setParent
{
  "parent": "parent-task-id",
  "insert_after": "sibling-task-id"
}
```

**Key Insights:**
- Relative positioning (before/after specific tasks)
- No direct position numbers exposed
- Context-aware (project/section specific)
- Natural for single-task moves

## Our Current Implementation

### Position System
- Integer position field with 1000-unit increments
- Separate position spaces per context (inbox, date-specific)
- Position range: typically starts at 1000, increments by 1000

### Current Capabilities (via PUT /api/tasks/{id})
1. **Direct position**: `{"position": 1500}`
2. **Insert after**: `{"insertAfterTaskId": "task-id"}`
3. **Move to top**: `{"insertAtTop": true}`

### Context Handling
- **Inbox**: Tasks without due date
- **Date context**: Tasks with same date (e.g., "date:2025-09-10")
- Each context maintains independent position sequence

## Chosen Approach: PATCH /api/tasks/{id}/move

### Design Decision
Implement a dedicated move endpoint that combines the best of Todoist and Asana approaches:

```
PATCH /api/tasks/{id}/move
Content-Type: application/json

{
  "insertAfter": "task-id",    // Position after specific task
  "insertBefore": "task-id",   // Position before specific task
  "moveToTop": true,           // Move to top of current context
  "moveToBottom": true         // Move to bottom of current context
}
```

### Why This Approach?

**Advantages:**
1. **Clear Intent**: Dedicated endpoint makes reordering operations explicit
2. **Flexible**: Supports both relative and absolute positioning strategies
3. **Simple**: One task at a time, easy to understand and implement
4. **RESTful**: PATCH is semantically correct for partial updates
5. **Context-Aware**: Automatically handles date/inbox contexts

**Trade-offs:**
- Multiple API calls needed for bulk reordering (acceptable for MVP)
- Slightly more complex than simple position update

### Implementation Strategy

#### Position Calculation
- **Insert After/Before**: Calculate midpoint between adjacent tasks
- **Move to Top**: Position = minPosition - 1000
- **Move to Bottom**: Position = maxPosition + 1000
- **Conflict Resolution**: If gap < 10, trigger rebalancing

#### Context Preservation
- Moving maintains current date/inbox context
- To change context, use standard PUT endpoint with new dueBy

#### Position Rebalancing (Future)
When positions get too close (gap < 10):
1. Get all tasks in context
2. Reassign positions with 1000 increments
3. Update all affected tasks

## Future Enhancements

### Bulk Reorder Endpoint (When Needed)
```
POST /api/tasks/reorder
{
  "context": "date:2025-09-10",  // Optional, defaults to current
  "tasks": [
    {"id": "task1", "position": 1000},
    {"id": "task2", "position": 2000},
    {"id": "task3", "position": 3000}
  ]
}
```

**Use Cases:**
- Drag-and-drop interfaces with multiple selections
- Bulk imports with specific ordering
- Performance optimization for large reorderings

### Smart Position Management
- Automatic rebalancing when positions cluster
- Floating point positions for infinite precision
- Position compression for old/archived tasks

## Testing Scenarios

### Basic Operations
1. Move task to top of inbox
2. Move task to bottom of date
3. Insert task between two others
4. Move when only one task exists

### Edge Cases
1. Insert between tasks with positions 1000 and 1001 (trigger rebalancing)
2. Move to top when position is already 0
3. Insert after non-existent task (error handling)
4. Move task in empty context

### Context Scenarios
1. Move within same date
2. Move within inbox
3. Verify position spaces remain separate

## API Documentation

### Request
```http
PATCH /api/tasks/{id}/move
Authorization: Required (via BFF headers)
Content-Type: application/json

{
  "insertAfter": "string",    // Task ID to insert after (optional)
  "insertBefore": "string",   // Task ID to insert before (optional)
  "moveToTop": boolean,       // Move to top of context (optional)
  "moveToBottom": boolean     // Move to bottom of context (optional)
}
```

### Response
```json
{
  "id": "task-id",
  "title": "Task Title",
  "position": 1500,
  "dueBy": {...},
  // ... other task fields
}
```

### Error Responses
- `400`: Invalid move operation (conflicting parameters)
- `404`: Task or reference task not found
- `403`: Task belongs to different user

## Implementation Checklist

- [ ] Create MoveTaskRequest DTO with validation
- [ ] Add PATCH /api/tasks/{id}/move endpoint
- [ ] Implement position calculation for each strategy
- [ ] Add authorization checks
- [ ] Handle edge cases and errors
- [ ] Write comprehensive tests
- [ ] Update Swagger documentation
- [ ] Add metrics/analytics tracking

## References

- [Todoist REST API v2](https://developer.todoist.com/rest/v2/)
- [Todoist Sync API v9](https://developer.todoist.com/sync/v9/)
- [Asana API - Tasks](https://developers.asana.com/reference/tasks)
- [REST API Design - Best Practices](https://restfulapi.net/)