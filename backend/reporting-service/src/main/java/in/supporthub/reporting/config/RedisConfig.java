package in.supporthub.reporting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis configuration for the reporting-service.
 *
 * <p>Redis is used exclusively for idempotency key storage in the Kafka consumers.
 * Each consumed event writes a key with a 24-hour TTL to prevent duplicate processing
 * on re-delivery.
 *
 * <p>Connection settings are supplied via Spring Boot auto-configuration using
 * {@code spring.data.redis.host} and {@code spring.data.redis.port} properties.
 */
@Configuration
public class RedisConfig {

    /**
     * Provides a {@link StringRedisTemplate} for idempotency key storage.
     *
     * <p>String keys are sufficient since idempotency values are always
     * simple {@code "1"} strings.
     *
     * @param connectionFactory the auto-configured Redis connection factory
     * @return configured StringRedisTemplate
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
