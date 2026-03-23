package in.supporthub.ordersync.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for the order-sync-service.
 *
 * <p>Uses a {@link StringRedisTemplate} for all cache operations.
 * Order objects are serialized to JSON strings before caching so that
 * the {@link ObjectMapper} handles type fidelity (Instants, BigDecimals, etc.).
 *
 * <p>Connection factory is auto-configured by Spring Boot from
 * {@code spring.data.redis.*} properties.
 */
@Configuration
public class RedisConfig {

    /**
     * StringRedisTemplate — keys and values are plain UTF-8 strings.
     * Order payloads are stored as JSON strings serialized by {@link ObjectMapper}.
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

    /**
     * Jackson {@link ObjectMapper} configured for Java 8 time types.
     *
     * <p>Used by {@link in.supporthub.ordersync.service.OmsClientService} to
     * serialize/deserialize {@link in.supporthub.ordersync.dto.OrderResponse} objects
     * for Redis caching. {@link java.time.Instant} values are written as ISO-8601 strings.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
