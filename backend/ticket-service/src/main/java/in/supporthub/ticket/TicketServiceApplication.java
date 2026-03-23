package in.supporthub.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SupportHub Ticket Service entry point.
 *
 * <p>Manages the full ticket lifecycle: creation, status transitions, SLA enforcement,
 * activity tracking, and ticket event publishing.
 * Runs on port 8082 (default).
 */
@SpringBootApplication
public class TicketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketServiceApplication.class, args);
    }
}
