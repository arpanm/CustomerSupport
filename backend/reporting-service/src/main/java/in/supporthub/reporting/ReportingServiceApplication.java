package in.supporthub.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SupportHub Reporting Service entry point.
 *
 * <p>Aggregates ticket events from Kafka to produce analytics:
 * ticket volume, SLA compliance rates, agent performance, and sentiment trends.
 * Runs on port 8087 (default).
 */
@SpringBootApplication
public class ReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}
