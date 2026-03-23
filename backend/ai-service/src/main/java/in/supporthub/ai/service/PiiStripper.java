package in.supporthub.ai.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Strips Personally Identifiable Information (PII) from text before it is sent to the AI model
 * or persisted in the audit log.
 *
 * <p>Patterns replaced:
 * <ul>
 *   <li>10-digit phone numbers (bare or with +91 / 0 prefix)</li>
 *   <li>Email addresses</li>
 * </ul>
 *
 * <p>All replacements use bracketed placeholders so the context is preserved for the AI
 * while the actual PII values are removed.
 */
@Component
public class PiiStripper {

    /** Matches Indian mobile numbers: +91-XXXXXXXXXX, +91 XXXXXXXXXX, 0XXXXXXXXXX, or bare 10-digit */
    private static final Pattern PHONE_WITH_COUNTRY_CODE =
            Pattern.compile("(?:\\+91[\\s-]?|0)?\\d{10}");

    /** Standard email address pattern. */
    private static final Pattern EMAIL =
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    /**
     * Returns a copy of {@code text} with all detected phone numbers and email addresses
     * replaced by {@code [PHONE]} and {@code [EMAIL]} respectively.
     *
     * @param text the raw input text (may be null)
     * @return sanitised text, or empty string if input is null
     */
    public String strip(String text) {
        if (text == null) {
            return "";
        }
        // Order matters: strip emails first, then phone numbers to avoid partial collisions
        String result = EMAIL.matcher(text).replaceAll("[EMAIL]");
        result = PHONE_WITH_COUNTRY_CODE.matcher(result).replaceAll("[PHONE]");
        return result;
    }
}
