package in.supporthub.auth.service;

/**
 * Contract for sending SMS OTP messages.
 *
 * <p>Implementations MUST NOT log phone numbers or OTP values.
 */
public interface SmsService {

    /**
     * Sends a 6-digit OTP to the given phone number.
     *
     * @param phone    E.164 phone number of the recipient (never log)
     * @param otp      6-digit one-time password (never log)
     * @param tenantId tenant UUID string — used for provider routing / logging context
     */
    void sendOtp(String phone, String otp, String tenantId);
}
