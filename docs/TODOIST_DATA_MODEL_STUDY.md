# Todoist Data Model Case Study

*A comprehensive analysis of Todoist's task management data architecture*

## Executive Summary

Todoist uses a sophisticated hybrid data model that balances user-friendly task semantics with technical requirements for calendar integration, timezone handling, and recurring tasks. This case study examines their approach to due dates, time handling, and data architecture based on their public API documentation and observed behaviors.

## Core Data Model

### Task Entity Structure

```json
{
  "id": "2995104339",
  "project_id": "220474322", 
  "section_id": "7025",
  "content": "Buy Milk",
  "description": "Need to get organic milk from the store",
  "is_completed": false,
  "labels": ["shopping", "urgent"],
  "parent_id": null,
  "order": 1,
  "priority": 1,
  "due": {
    "date": "2016-09-01",
    "datetime": "2016-09-01T09:00:00.000000Z", 
    "string": "tomorrow at 9am",
    "timezone": "Europe/Moscow",
    "is_recurring": false
  },
  "url": "https://todoist.com/showTask?id=2995104339",
  "comment_count": 0,
  "created_at": "2016-09-01T12:00:00.000000Z",
  "creator_id": "1855589",
  "assignee_id": "1855589",
  "assigner_id": "1855589",
  "duration": {
    "amount": 15,
    "unit": "minute"
  }
}
```

## Due Date Architecture

### The Hybrid Approach

Todoist employs a **multi-field due date system** that addresses different user scenarios:

#### 1. Date-Only Tasks (All-Day)
```json
"due": {
  "date": "2016-09-01",
  "datetime": null,
  "string": "Sep 1", 
  "timezone": null,
  "is_recurring": false
}
```

**Characteristics:**
- **User Intent**: "Finish this sometime on Sept 1"
- **Calendar Display**: All-day event 
- **Mental Model**: Deadline-focused, not time-blocked

#### 2. Time-Specific Tasks
```json
"due": {
  "date": "2016-09-01",
  "datetime": "2016-09-01T09:00:00.000000Z",
  "string": "tomorrow at 9am",
  "timezone": "Europe/Moscow", 
  "is_recurring": false
}
```

**Characteristics:**
- **User Intent**: "Complete by 9 AM specifically"
- **Calendar Display**: 1-hour time block (8-9 AM)
- **Mental Model**: Time-sensitive deadline

#### 3. Tasks with Duration
```json
"due": {
  "date": "2016-09-01", 
  "datetime": "2016-09-01T09:00:00.000000Z",
  "string": "tomorrow at 9am for 30 minutes",
  "timezone": "Europe/Moscow",
  "is_recurring": false
},
"duration": {
  "amount": 30,
  "unit": "minute" 
}
```

**Characteristics:**
- **User Intent**: "30-minute task, due by 9 AM"
- **Calendar Display**: 30-minute block (8:30-9:00 AM)
- **Mental Model**: Time-boxed work with deadline

## Key Design Decisions

### 1. Deadline-First Philosophy

**Tasks ≠ Events**
- Tasks represent **what needs to be done by when**
- Events represent **scheduled time blocks**
- Todoist models tasks as deadlines, not appointments

**Single Timestamp Approach:**
- `datetime` represents **due BY** this time
- Not "work FROM-TO this time" 
- Duration calculates backwards from deadline

### 2. Flexible Time Representation

**Multiple Time Formats:**
```json
"due": {
  "date": "2016-09-01",           // Always present - user's mental anchor
  "datetime": "...",              // Precise timestamp (UTC)
  "string": "tomorrow at 9am",    // Human-readable input
  "timezone": "Europe/Moscow"     // Context for interpretation
}
```

**Benefits:**
- **date**: Simple day-based organization
- **datetime**: Precise sorting and calendar integration
- **string**: Natural language input/display
- **timezone**: Accurate time interpretation

### 3. Calendar Integration Strategy

**Time Block Calculation:**
```
For timed task due at 9:00 AM:
├── Has duration (30 min) → Calendar: 8:30-9:00 AM
├── No duration → Calendar: 8:00-9:00 AM (default 1 hour)
└── Date only → Calendar: All-day event
```

**Why This Works:**
- Users think in deadlines: "Done by 9 AM"
- Calendar shows working time: "Block 8-9 AM to finish"
- Natural workflow: Reserve time before deadline

## Timezone Handling

### Storage Strategy
```json
"due": {
  "datetime": "2016-09-01T09:00:00.000000Z",  // Always UTC
  "timezone": "Europe/Moscow"                  // User context
}
```

### Time Types

**Source:** https://www.todoist.com/help/articles/set-a-fixed-time-or-floating-time-for-a-task-YUYVp27q

**1. Floating Time (Default)**
- **Default behavior**: All due times in Todoist are floating by default
- **Travel behavior**: Task time stays same local time across timezones
- **Example**: Task at "9 AM" in New York remains "9 AM" when traveling to London
- **Collaboration**: All collaborators see "9 AM" in their local timezone
- **Use cases**: 
  - Personal routines (morning workout, medication)
  - Daily habits (lunch break, evening review)
  - Self-care tasks that should adapt to local time

**2. Fixed Time**  
- **Opt-in feature**: Must be explicitly selected
- **Travel behavior**: Task time adjusts to maintain same global moment
- **Example**: "9 AM EST" task shows as "2 PM GMT" in London, "11 PM JST" in Tokyo
- **Collaboration**: Each collaborator sees converted time for their timezone
- **Use cases**:
  - Virtual meetings across timezones
  - Global deadlines
  - Coordinated team events
  - Time-sensitive deliverables

### Implementation Details

**Setting Time Type:**
1. Click Date field when adding/editing task
2. Click Time at bottom of scheduler
3. Enter due time
4. Click Time zone menu
5. Choose "Floating time" or "Fixed time"

**Limitations:**
- Can only select current timezone when setting fixed time
- If collaborator set different timezone, option appears to keep existing timezone
- Cannot change timezone retroactively without recreating task

## Completion Tracking

### Completion Data
```json
{
  "is_completed": true,
  "completed_at": "2016-09-01T09:11:00.000000Z"  // Precise UTC timestamp
}
```

**Design Choices:**
- **Creation**: `created_at` in UTC
- **Completion**: `completed_at` in UTC  
- **Due dates**: Flexible (date strings or UTC timestamps)
- **Analytics**: All based on precise UTC timestamps

## Recurring Tasks

### Recurrence Model
```json
"due": {
  "date": "2016-09-01",
  "datetime": "2016-09-01T09:00:00.000000Z",
  "string": "every day at 9am", 
  "timezone": "Europe/Moscow",
  "is_recurring": true
}
```

**Recurrence Logic:**
- Next occurrence calculated from completion time
- Maintains original timezone context
- Handles timezone transitions (DST, travel)

## API Design Patterns

### Input Flexibility
```bash
# Natural language
POST /tasks { "due_string": "tomorrow at 2pm" }

# Specific date  
POST /tasks { "due_date": "2016-09-01" }

# Precise timestamp
POST /tasks { "due_datetime": "2016-09-01T14:00:00Z" }
```

### Output Consistency
```json
// Always returns full due object
"due": {
  "date": "2016-09-01",
  "datetime": "2016-09-01T14:00:00.000000Z", 
  "string": "tomorrow at 2pm",
  "timezone": "America/New_York"
}
```

## Advantages of Todoist's Model

### ✅ User Experience Benefits

1. **Natural Input**: "tomorrow at 2pm" works
2. **Clear Intent**: Date-only vs time-specific is explicit  
3. **Flexible Display**: Can show date, time, or both as needed
4. **Calendar Integration**: Seamless sync with existing calendars

### ✅ Technical Benefits

1. **Timezone Robust**: Handles travel, DST, international users
2. **Sorting Friendly**: Can sort by date or precise time as needed
3. **Query Efficient**: Can filter by date strings or timestamps
4. **Migration Safe**: Adding time to date-only tasks is non-breaking

### ✅ Product Benefits  

1. **Progressive Disclosure**: Start simple (dates), add complexity (times) as needed
2. **Multiple Workflows**: Supports both deadline-focused and time-blocking users
3. **Integration Ready**: Works with calendars, reminders, external tools

## Potential Drawbacks

### ❌ Complexity
- Multiple fields for single concept (due date)
- API consumers must handle various date formats
- More complex database queries

### ❌ Storage Overhead
- Redundant information (date in both string and timestamp)
- Additional timezone and string fields
- Larger document size

### ❌ Consistency Challenges
- Date and datetime can get out of sync
- Timezone changes require careful handling
- Multiple representations of same information

## Lessons for Our System

### Key Takeaways

1. **Separate Concerns**: Date-only vs time-specific are different user intents
2. **Deadline Semantics**: Tasks are "due by" not "scheduled from-to"
3. **Progressive Enhancement**: Start with dates, add times when needed
4. **Calendar Integration**: Duration + deadline = calendar time block
5. **Timezone Context**: Store UTC + user timezone for flexibility

### Recommended Approach

```kotlin
data class Task(
    // Core due date (always present for tasks with dates)
    val dueDate: String? = null,        // "2025-09-05" - ISO date string
    
    // Optional precise timing
    val dueTime: Instant? = null,       // UTC timestamp - null = date-only
    val timezone: String? = null,       // User timezone when time was set
    
    // Optional duration for calendar integration  
    val duration: Int? = null,          // Minutes
    
    // Standard fields
    val completed: Boolean = false,
    val completedAt: Instant? = null    // Always UTC timestamp
)
```

### Implementation Strategy

1. **Phase 1**: Date-only tasks with timezone-aware "today" logic
2. **Phase 2**: Add optional time support with duration
3. **Phase 3**: Calendar integration and advanced features

This approach follows Todoist's proven patterns while fitting our specific needs and technical constraints.

## Conclusion

Todoist's data model demonstrates sophisticated thinking about task management semantics. Their hybrid approach successfully bridges the gap between user mental models (dates and deadlines) and technical requirements (precise timestamps and calendar integration).

The key insight is treating tasks as **deadline-focused entities** rather than time-blocked events, while providing the flexibility to add timing precision when needed. This creates a natural progression from simple date-based task management to complex time-aware productivity workflows.

For our Havq task service, adopting similar patterns would provide both immediate simplicity and long-term flexibility, supporting users from basic task capture through advanced calendar integration and time-blocking workflows.