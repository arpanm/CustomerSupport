package in.supporthub.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SupportHub AI Service entry point.
 *
 * <p>Performs AI processing via the Anthropic Claude API:
 * <ul>
 *   <li>Sentiment analysis using claude-haiku-4-5-20251001</li>
 *   <li>Resolution suggestions using claude-sonnet-4-5</li>
 *   <li>Ticket embedding generation for RAG (pgvector)</li>
 * </ul>
 * Runs on port 8084 (default).
 */
@SpringBootApplication
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
