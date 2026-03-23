package in.supporthub.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Year;
import java.util.UUID;

/**
 * Generates unique, human-readable ticket numbers using Redis INCR.
 *
 * <p>Format: {@code {PREFIX}-{YEAR}-{SEQ:06d}}<br>
 * Example: {@code FC-2024-001234}
 *
 * <p>The sequence counter is stored in Redis as {@code ticket:seq:{tenantId}:{year}}.
 * A TTL of 400 days is set on first use so that keys expire automatically after the year rolls over.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TicketNumberGenerator {

    /**
     * Redis key TTL — 400 days ensures the key outlives the year and is cleaned up automatically.
     */
    private static final Duration SEQ_KEY_TTL = Duration.ofDays(400);

    private final StringRedisTemplate redisTemplate;

    /**
     * Generates the next ticket number for the given tenant.
     *
     * @param tenantId the tenant UUID (used to namespace the sequence)
     * @param prefix   the tenant-specific prefix (e.g., "FC")
     * @return the formatted ticket number (e.g., "FC-2024-001234")
     */
    public String generate(UUID tenantId, String prefix) {
        int year = Year.now().getValue();
        String key = "ticket:seq:" + tenantId + ":" + year;

        Long seq = redisTemplate.opsForValue().increment(key);
        if (seq == null) {
            throw new IllegalStateException(
                    "Failed to generate ticket sequence from Redis: tenantId=" + tenantId);
        }

        // Set TTL only on first use (when seq == 1) to avoid resetting it on every increment
        if (seq == 1L) {
            redisTemplate.expire(key, SEQ_KEY_TTL);
        }

        String ticketNumber = String.format("%s-%d-%06d", prefix, year, seq);
        log.info("Generated ticket number: tenantId={}, ticketNumber={}", tenantId, ticketNumber);
        return ticketNumber;
    }
}
