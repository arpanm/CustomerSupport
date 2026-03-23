package in.supporthub.ticket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled task execution.
 *
 * <p>Required for the {@link in.supporthub.ticket.service.SlaEngine#detectBreaches()} job
 * to run on its fixed 5-minute delay.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
