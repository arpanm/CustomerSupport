package in.supporthub.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SupportHub Customer Service entry point.
 *
 * <p>Manages customer profiles, contact details (PII-encrypted), and lookup by phone/email.
 * Runs on port 8083 (default).
 */
@SpringBootApplication
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
