// MongoDB collection setup for ai_interactions
db.createCollection("ai_interactions", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["tenantId", "ticketId", "interactionType", "model", "createdAt"],
      properties: {
        tenantId:        { bsonType: "string" },
        ticketId:        { bsonType: "string" },
        interactionType: { bsonType: "string", enum: ["SENTIMENT", "RESOLUTION_SUGGESTION"] },
        prompt:          { bsonType: "string" },
        response:        { bsonType: "string" },
        model:           { bsonType: "string" },
        inputTokens:     { bsonType: "int" },
        outputTokens:    { bsonType: "int" },
        latencyMs:       { bsonType: "long" },
        createdAt:       { bsonType: "date" }
      }
    }
  }
});

db.ai_interactions.createIndex({ tenantId: 1, ticketId: 1 });
db.ai_interactions.createIndex({ tenantId: 1, interactionType: 1, createdAt: -1 });
db.ai_interactions.createIndex({ createdAt: 1 }, { expireAfterSeconds: 7776000 }); // 90 days TTL
