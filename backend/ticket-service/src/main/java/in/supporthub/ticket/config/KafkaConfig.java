package in.supporthub.ticket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Kafka configuration for the ticket service.
 *
 * <p>Provides a {@link KafkaTemplate} with {@code String} keys and generic {@code Object} values
 * for publishing ticket events.
 */
@Configuration
public class KafkaConfig {

    /**
     * Creates the shared {@link KafkaTemplate} used by {@link in.supporthub.ticket.service.TicketEventPublisher}.
     *
     * @param producerFactory the auto-configured producer factory
     * @return configured KafkaTemplate
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
