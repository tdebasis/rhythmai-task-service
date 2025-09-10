/**
 * MongoDB Migration Script: Convert to Hybrid Date Model
 * 
 * This script migrates existing tasks from the old single dueDate field
 * to the new hybrid date model with dueByDate, dueByTime, dueTimezone, and timeType.
 * 
 * Usage:
 * mongosh havq-tasks-local scripts/migrate-hybrid-dates.js
 */

// Switch to the correct database
use('havq-tasks-local');

print("Starting migration to hybrid date model...");

// Count existing tasks that need migration
const tasksToMigrate = db['havq-tasks'].countDocuments({ 
    dueDate: { $exists: true },
    dueByDate: { $exists: false }
});

print(`Found ${tasksToMigrate} tasks to migrate`);

if (tasksToMigrate === 0) {
    print("No tasks need migration. Exiting...");
    quit();
}

// Perform the migration
const result = db['havq-tasks'].updateMany(
    { 
        dueDate: { $exists: true },
        dueByDate: { $exists: false }
    },
    [
        {
            $set: {
                // Extract date string from existing Instant (YYYY-MM-DD format)
                dueByDate: {
                    $dateToString: {
                        format: "%Y-%m-%d",
                        date: "$dueDate",
                        timezone: "UTC"
                    }
                },
                // Keep original timestamp as dueByTime
                dueByTime: "$dueDate",
                // Assume UTC for all existing tasks
                dueTimezone: "UTC",
                // All existing tasks become FIXED time type
                timeType: "FIXED"
            }
        }
    ]
);

print(`Migration completed: ${result.modifiedCount} tasks updated`);

// Remove old dueDate field after migration
print("Removing old dueDate field...");
const cleanupResult = db['havq-tasks'].updateMany(
    { dueDate: { $exists: true } },
    { $unset: { dueDate: "" } }
);

print(`Cleanup completed: ${cleanupResult.modifiedCount} tasks cleaned`);

// Verify migration
const sampleTask = db['havq-tasks'].findOne({ dueByDate: { $exists: true } });
if (sampleTask) {
    print("\nSample migrated task:");
    print(`  Title: ${sampleTask.title}`);
    print(`  DueByDate: ${sampleTask.dueByDate}`);
    print(`  DueByTime: ${sampleTask.dueByTime}`);
    print(`  DueTimezone: ${sampleTask.dueTimezone}`);
    print(`  TimeType: ${sampleTask.timeType}`);
}

// Final verification
const remainingOldTasks = db['havq-tasks'].countDocuments({ 
    dueDate: { $exists: true }
});

const migratedTasks = db['havq-tasks'].countDocuments({ 
    dueByDate: { $exists: true }
});

print("\n=== Migration Summary ===");
print(`Tasks migrated: ${migratedTasks}`);
print(`Tasks with old schema: ${remainingOldTasks}`);

if (remainingOldTasks > 0) {
    print("WARNING: Some tasks still have the old schema!");
} else {
    print("SUCCESS: All tasks have been migrated to the new hybrid date model!");
}

// Create index on new fields for query performance
print("\nCreating indexes on new date fields...");
db['havq-tasks'].createIndex({ "userId": 1, "dueByDate": 1 });
db['havq-tasks'].createIndex({ "userId": 1, "dueByTime": 1 });
db['havq-tasks'].createIndex({ "userId": 1, "timeType": 1 });

print("Indexes created successfully!");
print("\nMigration script completed.");