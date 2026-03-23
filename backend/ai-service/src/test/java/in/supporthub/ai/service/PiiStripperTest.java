package in.supporthub.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PiiStripper}.
 *
 * <p>Verifies that phone numbers (bare 10-digit, +91-prefixed), email addresses,
 * and mixed PII are correctly replaced with safe placeholders.
 */
class PiiStripperTest {

    private PiiStripper piiStripper;

    @BeforeEach
    void setUp() {
        piiStripper = new PiiStripper();
    }

    @Test
    @DisplayName("Bare 10-digit phone number is replaced with [PHONE]")
    void shouldStripBarePhoneNumber() {
        String input = "Please call me on 9876543210 urgently.";
        String result = piiStripper.strip(input);
        assertThat(result).contains("[PHONE]");
        assertThat(result).doesNotContain("9876543210");
    }

    @Test
    @DisplayName("Phone number with +91 prefix is replaced with [PHONE]")
    void shouldStripPhoneWithCountryCode() {
        String input = "My number is +919876543210";
        String result = piiStripper.strip(input);
        assertThat(result).contains("[PHONE]");
        assertThat(result).doesNotContain("9876543210");
    }

    @Test
    @DisplayName("Phone number with +91- dash format is replaced with [PHONE]")
    void shouldStripPhoneWithDashFormat() {
        String input = "Contact: +91-9876543210";
        String result = piiStripper.strip(input);
        assertThat(result).contains("[PHONE]");
        assertThat(result).doesNotContain("9876543210");
    }

    @Test
    @DisplayName("Email address is replaced with [EMAIL]")
    void shouldStripEmail() {
        String input = "Send refund to customer@example.com please.";
        String result = piiStripper.strip(input);
        assertThat(result).contains("[EMAIL]");
        assertThat(result).doesNotContain("customer@example.com");
    }

    @Test
    @DisplayName("Text with no PII is returned unchanged")
    void shouldLeaveCleanTextUnchanged() {
        String input = "My order number is ORD-2024-001234 and it has not arrived.";
        String result = piiStripper.strip(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("Text with both phone and email has both stripped")
    void shouldStripMixedPii() {
        String input = "Call me at 9876543210 or email john.doe@food.in for the refund.";
        String result = piiStripper.strip(input);
        assertThat(result).contains("[PHONE]");
        assertThat(result).contains("[EMAIL]");
        assertThat(result).doesNotContain("9876543210");
        assertThat(result).doesNotContain("john.doe@food.in");
    }

    @Test
    @DisplayName("Null input returns empty string")
    void shouldHandleNullInput() {
        String result = piiStripper.strip(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Empty string is returned unchanged")
    void shouldHandleEmptyInput() {
        String result = piiStripper.strip("");
        assertThat(result).isEmpty();
    }
}
