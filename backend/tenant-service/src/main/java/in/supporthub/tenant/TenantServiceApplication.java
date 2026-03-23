package in.supporthub.tenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SupportHub Tenant Service entry point.
 *
 * <p>Manages tenant lifecycle: provisioning, configuration (SLA rules, branding,
 * feature flags), agent accounts, and subscription/billing state.
 * Runs on port 8088 (default).
 */
@SpringBootApplication
public class TenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenantServiceApplication.class, args);
    }
}
