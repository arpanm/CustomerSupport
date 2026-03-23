// MongoDB collection setup for faq_cms_sync_log
db.createCollection("faq_cms_sync_log");

db.faq_cms_sync_log.createIndex({ tenantId: 1, strapiId: 1 });
db.faq_cms_sync_log.createIndex({ processedAt: -1 });
db.faq_cms_sync_log.createIndex({ processedAt: 1 }, { expireAfterSeconds: 604800 }); // 7 days TTL

// Example document structure (for documentation):
// {
//   tenantId: "uuid",
//   strapiId: "123",
//   event: "entry.create|entry.update|entry.delete|entry.publish",
//   status: "SUCCESS|FAILED",
//   faqEntryId: "uuid",
//   errorMessage: "string (if failed)",
//   processedAt: ISODate()
// }
