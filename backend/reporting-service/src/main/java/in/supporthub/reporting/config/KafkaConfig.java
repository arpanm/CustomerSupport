package in.supporthub.reporting.config;

import in.supporthub.shared.event.SentimentAnalysisCompletedEvent;
import in.supporthub.shared.event.TicketCreatedEvent;
import in.supporthub.shared.event.TicketStatusChangedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for the reporting-service.
 *
 * <p>Three consumer factories are configured — one per consumed topic — to allow
 * each consumer to deserialize the correct event type without ambiguity.
 *
 * <p>Topics consumed:
 * <ul>
 *   <li>{@code ticket.created} → {@link TicketCreatedEvent}</li>
 *   <li>{@code ticket.status-changed} → {@link TicketStatusChangedEvent}</li>
 *   <li>{@code ai.sentiment-analysis-completed} → {@link SentimentAnalysisCompletedEvent}</li>
 * </ul>
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:reporting-service}")
    private String groupId;

    // -------------------------------------------------------------------------
    // ticket.created consumer
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, TicketCreatedEvent> ticketCreatedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                new JsonDeserializer<>(TicketCreatedEvent.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TicketCreatedEvent>
    ticketCreatedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TicketCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(ticketCreatedConsumerFactory());
        return factory;
    }

    // -------------------------------------------------------------------------
    // ticket.status-changed consumer
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, TicketStatusChangedEvent> ticketStatusChangedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                new JsonDeserializer<>(TicketStatusChangedEvent.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TicketStatusChangedEvent>
    ticketStatusChangedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TicketStatusChangedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(ticketStatusChangedConsumerFactory());
        return factory;
    }

    // -------------------------------------------------------------------------
    // ai.sentiment-analysis-completed consumer
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, SentimentAnalysisCompletedEvent> sentimentConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                new JsonDeserializer<>(SentimentAnalysisCompletedEvent.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SentimentAnalysisCompletedEvent>
    sentimentKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, SentimentAnalysisCompletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sentimentConsumerFactory());
        return factory;
    }

    // -------------------------------------------------------------------------
    // Shared consumer properties
    // -------------------------------------------------------------------------

    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        return props;
    }
}
