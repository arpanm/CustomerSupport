package in.supporthub.ticket.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a ticket category is not found or does not belong to the tenant.
 */
public class CategoryNotFoundException extends AppException {

    /**
     * @param categoryId the category UUID that was not found
     */
    public CategoryNotFoundException(UUID categoryId) {
        super("CATEGORY_NOT_FOUND", "Category not found: " + categoryId, HttpStatus.NOT_FOUND);
    }
}
