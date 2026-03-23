package in.supporthub.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Common Spring configuration shared across all SupportHub services.
 *
 * <p>Services that import this module will inherit these beans. Per the
 * CLAUDE.md No-Go List, services MUST NOT create {@code new ObjectMapper()} — they
 * must inject the Spring-managed bean configured here.
 */
@Configuration
public class CommonConfig {

    /**
     * Shared {@link ObjectMapper} bean used for all JSON serialisation across the application.
     *
     * <p>Configuration:
     * <ul>
     *   <li>Registers {@link JavaTimeModule} so {@code Instant}, {@code LocalDate}, etc.
     *       serialise/deserialise correctly.</li>
     *   <li>Disables {@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} so dates are
     *       written as ISO-8601 strings (e.g., {@code "2026-03-23T10:00:00Z"}) rather than
     *       Unix epoch numbers.</li>
     * </ul>
     *
     * @return Configured {@link ObjectMapper}.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
