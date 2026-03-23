package in.supporthub.notification.service;

import in.supporthub.notification.exception.PiiDecryptionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Base64;

/**
 * Service for decrypting AES-256-GCM encrypted PII fields (e.g., phone numbers).
 *
 * <p>Encrypted data format (after Base64 decode):
 * <pre>
 *   [12 bytes IV][ciphertext + 16-byte GCM auth tag]
 * </pre>
 *
 * <p>SECURITY RULES:
 * <ul>
 *   <li>NEVER log the decrypted value.</li>
 *   <li>NEVER log the encrypted bytes or secret key.</li>
 *   <li>Only use the decrypted value for API call payloads, never for logs or audit trails.</li>
 * </ul>
 */
@Service
public class PiiDecryptionService {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String KEY_ALGORITHM = "AES";

    @Value("${encryption.secret-key:}")
    private String secretKeyBase64;

    /**
     * Decrypts an AES-256-GCM encrypted byte array.
     *
     * <p>Expected format: first 12 bytes are the IV, remaining bytes are ciphertext + GCM tag.
     * The secret key must be provided as a Base64-encoded 32-byte (256-bit) value via
     * {@code encryption.secret-key}.
     *
     * @param encrypted AES-GCM encrypted bytes (IV prepended).
     * @return Decrypted plaintext string.
     * @throws PiiDecryptionException if decryption fails for any reason.
     */
    public String decrypt(byte[] encrypted) {
        if (secretKeyBase64 == null || secretKeyBase64.isBlank()) {
            throw new PiiDecryptionException("Encryption secret key is not configured");
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
            SecretKey secretKey = new SecretKeySpec(keyBytes, KEY_ALGORITHM);

            ByteBuffer buffer = ByteBuffer.wrap(encrypted);

            // Extract IV (first 12 bytes)
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            // Extract ciphertext (remaining bytes)
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decrypted = cipher.doFinal(ciphertext);
            return new String(decrypted);

        } catch (Exception e) {
            // Do not include any encrypted bytes or key material in the error message
            throw new PiiDecryptionException("PII decryption failed", e);
        }
    }

    /**
     * Decrypts a Base64-encoded, AES-256-GCM encrypted PII string.
     *
     * <p>Convenience overload for values stored as Base64 strings in the database.
     *
     * @param encryptedBase64 Base64-encoded encrypted value.
     * @return Decrypted plaintext string.
     * @throws PiiDecryptionException if decryption fails.
     */
    public String decryptBase64(String encryptedBase64) {
        try {
            byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);
            return decrypt(encrypted);
        } catch (PiiDecryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new PiiDecryptionException("Failed to decode Base64-encoded PII value", e);
        }
    }
}
