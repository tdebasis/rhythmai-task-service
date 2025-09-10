// MongoDB Migration Script for CompletedOn
// Run this script with: mongosh havq migrate-completed-on.js

print("Starting CompletedOn migration...");

// Count tasks that need migration
const needsMigration = db["havq-tasks"].countDocuments({
  $or: [
    { completedAt: { $exists: true } },
    { completedDate: { $exists: true } }
  ],
  completedOn: { $exists: false }
});

print(`Found ${needsMigration} tasks that need migration`);

if (needsMigration > 0) {
  // Migrate tasks with both completedAt and completedDate
  const result1 = db["havq-tasks"].updateMany(
    {
      completedAt: { $exists: true },
      completedDate: { $exists: true },
      completedOn: { $exists: false }
    },
    [
      {
        $set: {
          completedOn: {
            date: "$completedDate",
            time: "$completedAt",
            timeType: "FIXED"
          }
        }
      },
      {
        $unset: ["completedAt", "completedDate"]
      }
    ]
  );
  
  print(`Migrated ${result1.modifiedCount} tasks with both completedAt and completedDate`);
  
  // Migrate tasks with only completedAt (derive date from timestamp)
  const tasksWithOnlyTimestamp = db["havq-tasks"].find({
    completedAt: { $exists: true },
    completedDate: { $exists: false },
    completedOn: { $exists: false }
  }).toArray();
  
  tasksWithOnlyTimestamp.forEach(task => {
    const date = new Date(task.completedAt);
    const dateStr = date.toISOString().split('T')[0]; // YYYY-MM-DD format
    
    db["havq-tasks"].updateOne(
      { _id: task._id },
      {
        $set: {
          completedOn: {
            date: dateStr,
            time: task.completedAt,
            timeType: "FIXED"
          }
        },
        $unset: { completedAt: "", completedDate: "" }
      }
    );
  });
  
  print(`Migrated ${tasksWithOnlyTimestamp.length} tasks with only completedAt`);
  
  // Migrate tasks with only completedDate (create timestamp at start of day)
  const tasksWithOnlyDate = db["havq-tasks"].find({
    completedDate: { $exists: true },
    completedAt: { $exists: false },
    completedOn: { $exists: false }
  }).toArray();
  
  tasksWithOnlyDate.forEach(task => {
    const timestamp = new Date(task.completedDate + "T00:00:00Z");
    
    db["havq-tasks"].updateOne(
      { _id: task._id },
      {
        $set: {
          completedOn: {
            date: task.completedDate,
            time: timestamp,
            timeType: "FIXED"
          }
        },
        $unset: { completedAt: "", completedDate: "" }
      }
    );
  });
  
  print(`Migrated ${tasksWithOnlyDate.length} tasks with only completedDate`);
}

// Final verification
const remaining = db["havq-tasks"].countDocuments({
  $or: [
    { completedAt: { $exists: true } },
    { completedDate: { $exists: true } }
  ]
});

const migrated = db["havq-tasks"].countDocuments({
  completedOn: { $exists: true }
});

print("\n=== Migration Complete ===");
print(`Tasks with completedOn: ${migrated}`);
print(`Tasks with legacy fields remaining: ${remaining}`);

if (remaining > 0) {
  print("⚠️  Warning: Some tasks still have legacy fields. Manual review may be needed.");
} else {
  print("✅ All tasks successfully migrated!");
}