package in.supporthub.faq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SupportHub FAQ Service entry point.
 *
 * <p>Manages FAQ content (sourced from Strapi CMS) and provides semantic search
 * via Elasticsearch full-text search and pgvector similarity search.
 * Runs on port 8086 (default).
 */
@SpringBootApplication
public class FaqServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FaqServiceApplication.class, args);
    }
}
