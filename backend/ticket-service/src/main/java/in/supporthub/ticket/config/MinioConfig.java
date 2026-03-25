package in.supporthub.ticket.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the MinIO client used by {@link in.supporthub.ticket.service.AttachmentService}
 * to generate presigned PUT URLs for file uploads.
 *
 * <p>Connection parameters are read from {@code supporthub.minio.*} properties
 * (see {@code application.yml}). Never hardcode credentials here.
 */
@Configuration
public class MinioConfig {

    @Value("${supporthub.minio.endpoint}")
    private String endpoint;

    @Value("${supporthub.minio.access-key}")
    private String accessKey;

    @Value("${supporthub.minio.secret-key}")
    private String secretKey;

    /**
     * Creates the shared {@link MinioClient} singleton.
     *
     * @return configured MinioClient
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
