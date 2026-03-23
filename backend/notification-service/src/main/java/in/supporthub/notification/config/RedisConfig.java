package in.supporthub.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for the notification-service.
 *
 * <p>Provides a {@link StringRedisTemplate} used for idempotency key storage
 * in the Kafka consumer. Keys use the format {@code notif:processed:{eventId}}.
 */
@Configuration
public class RedisConfig {

    /**
     * StringRedisTemplate using String serializers for both key and value.
     * Suitable for idempotency keys and simple counters.
     *
     * @param connectionFactory Auto-configured by Spring Boot from application properties.
     * @return Configured {@link StringRedisTemplate}.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
