package in.supporthub.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SupportHub Notification Service entry point.
 *
 * <p>Sends multi-channel notifications triggered by Kafka events:
 * <ul>
 *   <li>SMS via MSG91</li>
 *   <li>Email via SendGrid</li>
 *   <li>WhatsApp via Meta Business API</li>
 * </ul>
 * Runs on port 8085 (default).
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
