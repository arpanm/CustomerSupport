// MongoDB collection setup for notifications
db.createCollection("notifications", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["tenantId", "recipientId", "recipientType", "channel", "status", "createdAt"],
      properties: {
        tenantId:      { bsonType: "string" },
        recipientId:   { bsonType: "string" },
        recipientType: { bsonType: "string", enum: ["CUSTOMER", "AGENT"] },
        channel:       { bsonType: "string", enum: ["SMS", "EMAIL", "IN_APP", "WHATSAPP"] },
        status:        { bsonType: "string", enum: ["PENDING", "SENT", "FAILED", "DELIVERED"] },
        subject:       { bsonType: "string" },
        content:       { bsonType: "string" },
        referenceId:   { bsonType: "string" },
        referenceType: { bsonType: "string" },
        attempts:      { bsonType: "int" },
        createdAt:     { bsonType: "date" }
      }
    }
  }
});

db.notifications.createIndex({ tenantId: 1, recipientId: 1, status: 1, createdAt: -1 });
db.notifications.createIndex({ tenantId: 1, referenceId: 1, referenceType: 1 });
db.notifications.createIndex({ createdAt: 1 }, { expireAfterSeconds: 2592000 }); // 30 days TTL
