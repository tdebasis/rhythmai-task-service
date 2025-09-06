/**
 * MongoDB Rollback Script: Revert from Hybrid Date Model
 * 
 * This script rolls back the hybrid date model migration,
 * converting tasks back to the original single dueDate field.
 * 
 * WARNING: This will lose timezone and time type information!
 * 
 * Usage:
 * mongosh rhythmai-tasks-local scripts/rollback-hybrid-dates.js
 */

// Switch to the correct database
use('rhythmai-tasks-local');

print("Starting rollback from hybrid date model...");
print("WARNING: This will lose timezone and time type information!");

// Count tasks to rollback
const tasksToRollback = db['rhythmai-tasks'].countDocuments({ 
    dueByDate: { $exists: true }
});

print(`Found ${tasksToRollback} tasks to rollback`);

if (tasksToRollback === 0) {
    print("No tasks need rollback. Exiting...");
    quit();
}

// Confirm rollback
print("\nPress Ctrl+C within 5 seconds to cancel...");
sleep(5000);

// Perform the rollback
const result = db['rhythmai-tasks'].updateMany(
    { dueByDate: { $exists: true } },
    [
        {
            $set: {
                // Use dueByTime if it exists, otherwise create from dueByDate
                dueDate: {
                    $cond: {
                        if: { $ne: ["$dueByTime", null] },
                        then: "$dueByTime",
                        else: {
                            $dateFromString: {
                                dateString: { $concat: ["$dueByDate", "T00:00:00.000Z"] }
                            }
                        }
                    }
                }
            }
        }
    ]
);

print(`Rollback updated: ${result.modifiedCount} tasks`);

// Remove hybrid date fields
print("Removing hybrid date fields...");
const cleanupResult = db['rhythmai-tasks'].updateMany(
    { dueByDate: { $exists: true } },
    { 
        $unset: { 
            dueByDate: "",
            dueByTime: "",
            dueTimezone: "",
            timeType: ""
        } 
    }
);

print(`Cleanup completed: ${cleanupResult.modifiedCount} tasks cleaned`);

// Verify rollback
const sampleTask = db['rhythmai-tasks'].findOne({ dueDate: { $exists: true } });
if (sampleTask) {
    print("\nSample rolled-back task:");
    print(`  Title: ${sampleTask.title}`);
    print(`  DueDate: ${sampleTask.dueDate}`);
}

// Final verification
const remainingHybridTasks = db['rhythmai-tasks'].countDocuments({ 
    dueByDate: { $exists: true }
});

const rolledBackTasks = db['rhythmai-tasks'].countDocuments({ 
    dueDate: { $exists: true }
});

print("\n=== Rollback Summary ===");
print(`Tasks rolled back: ${rolledBackTasks}`);
print(`Tasks with hybrid schema: ${remainingHybridTasks}`);

if (remainingHybridTasks > 0) {
    print("WARNING: Some tasks still have the hybrid schema!");
} else {
    print("SUCCESS: All tasks have been rolled back to the original schema!");
}

// Drop indexes on hybrid fields
print("\nDropping indexes on hybrid date fields...");
try {
    db['rhythmai-tasks'].dropIndex({ "userId": 1, "dueByDate": 1 });
    db['rhythmai-tasks'].dropIndex({ "userId": 1, "dueByTime": 1 });
    db['rhythmai-tasks'].dropIndex({ "userId": 1, "timeType": 1 });
    print("Indexes dropped successfully!");
} catch (e) {
    print("Note: Some indexes may not exist, which is fine.");
}

print("\nRollback script completed.");