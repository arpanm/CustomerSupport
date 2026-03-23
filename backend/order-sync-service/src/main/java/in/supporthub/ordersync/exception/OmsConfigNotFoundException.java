package in.supporthub.ordersync.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when no OMS configuration is found for a given tenant.
 */
public class OmsConfigNotFoundException extends AppException {

    public OmsConfigNotFoundException(UUID tenantId) {
        super(
                "OMS_CONFIG_NOT_FOUND",
                "No OMS configuration found for tenantId: " + tenantId,
                HttpStatus.NOT_FOUND
        );
    }
}
