package in.supporthub.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis configuration for the ai-service.
 *
 * <p>Provides a {@link StringRedisTemplate} used by the Kafka consumer for idempotency
 * key storage ({@code ai:processed:{eventId}}).
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a {@link StringRedisTemplate} for string key/value operations.
     *
     * @param connectionFactory the auto-configured Redis connection factory
     * @return configured StringRedisTemplate
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
