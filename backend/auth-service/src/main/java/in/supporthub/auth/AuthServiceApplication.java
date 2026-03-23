package in.supporthub.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SupportHub Auth Service entry point.
 *
 * <p>Handles OTP-based login, JWT issuance and refresh, and session management.
 * Runs on port 8081 (default).
 */
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
