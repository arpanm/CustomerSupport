package in.supporthub.ordersync.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption/decryption service for sensitive fields (e.g., OMS API keys).
 *
 * <p>Storage format: {@code [12-byte IV][ciphertext+16-byte auth tag]}.
 * The IV is randomly generated per encryption operation and prepended to the ciphertext
 * so that it is available for decryption without additional storage.
 *
 * <p>The secret key must be a Base64-encoded 32-byte (256-bit) value supplied via
 * the {@code encryption.secret-key} property.
 *
 * <p>NEVER log or expose the secret key or any plaintext value.
 */
@Service
public class PiiEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public PiiEncryptionService(@Value("${encryption.secret-key}") String base64SecretKey) {
        if (base64SecretKey == null || base64SecretKey.isBlank()) {
            throw new IllegalStateException(
                    "encryption.secret-key must be set to a non-empty Base64-encoded 32-byte value");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64SecretKey);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "encryption.secret-key must decode to exactly 32 bytes for AES-256; got "
                    + keyBytes.length + " bytes");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts the given plaintext string using AES-256-GCM.
     *
     * @param plaintext the value to encrypt; must not be null
     * @return ciphertext bytes in the format: [12-byte IV][encrypted data + 16-byte GCM tag]
     */
    public byte[] encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encryptedData = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(IV_LENGTH_BYTES + encryptedData.length);
            buffer.put(iv);
            buffer.put(encryptedData);
            return buffer.array();
        } catch (Exception ex) {
            throw new IllegalStateException("Encryption failed", ex);
        }
    }

    /**
     * Decrypts ciphertext produced by {@link #encrypt(String)}.
     *
     * @param ciphertext bytes in the format: [12-byte IV][encrypted data + 16-byte GCM tag]
     * @return the original plaintext string
     */
    public String decrypt(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length <= IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("ciphertext is null or too short to contain an IV");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(ciphertext);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] encryptedData = new byte[buffer.remaining()];
            buffer.get(encryptedData);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] decryptedBytes = cipher.doFinal(encryptedData);
            return new String(decryptedBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Decryption failed", ex);
        }
    }
}
