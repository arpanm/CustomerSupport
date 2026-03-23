package in.supporthub.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.supporthub.auth.exception.OtpExpiredException;
import in.supporthub.auth.exception.OtpInvalidException;
import in.supporthub.auth.exception.OtpRateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OtpService}.
 *
 * <p>All tests mock {@link StringRedisTemplate} to avoid needing a live Redis instance.
 */
@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final String PHONE = "+919876543210";
    private static final String TENANT_ID = "tenant-123";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        otpService = new OtpService(stringRedisTemplate, new ObjectMapper());
    }

    // -----------------------------------------------------------------------
    // generateAndStoreOtp
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("generateAndStoreOtp: stores OTP in Redis with correct key prefix and TTL")
    void generateAndStoreOtp_storesWithCorrectKeyAndTtl() {
        // ARRANGE — no existing rate-limit counter
        when(valueOps.get(anyString())).thenReturn(null);

        // ACT
        String otp = otpService.generateAndStoreOtp(PHONE, TENANT_ID);

        // ASSERT — OTP is a 6-digit numeric string
        assertThat(otp).matches("^[0-9]{6}$");

        // Verify the OTP was stored with correct key prefix and TTL
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);

        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture(), eq(TimeUnit.SECONDS));

        assertThat(keyCaptor.getValue()).startsWith("otp:" + TENANT_ID + ":");
        assertThat(ttlCaptor.getValue()).isEqualTo(300L);
        assertThat(valueCaptor.getValue()).contains("\"otp\":\"" + otp + "\"");
        assertThat(valueCaptor.getValue()).contains("\"attempts\":0");
    }

    @Test
    @DisplayName("generateAndStoreOtp: throws OtpRateLimitException when send count reaches 3")
    void generateAndStoreOtp_throwsRateLimitException_whenMaxSendsExceeded() {
        // ARRANGE — rate-limit counter already at 3
        when(valueOps.get(anyString())).thenReturn("3");

        // ACT + ASSERT
        assertThatThrownBy(() -> otpService.generateAndStoreOtp(PHONE, TENANT_ID))
                .isInstanceOf(OtpRateLimitException.class);

        // OTP must NOT be stored when rate-limited
        verify(valueOps, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("generateAndStoreOtp: increments send counter for rate limiting")
    void generateAndStoreOtp_incrementsSendCounter() {
        // ARRANGE
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);

        // ACT
        otpService.generateAndStoreOtp(PHONE, TENANT_ID);

        // ASSERT — increment called once for the rate-limit key
        verify(valueOps, times(1)).increment(anyString());
        // TTL set on the rate-limit bucket on first increment
        verify(stringRedisTemplate, times(1)).expire(anyString(), eq(3600L), eq(TimeUnit.SECONDS));
    }

    // -----------------------------------------------------------------------
    // verifyOtp
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("verifyOtp: returns true when OTP matches")
    void verifyOtp_returnsTrue_whenOtpMatches() {
        // ARRANGE
        String storedJson = "{\"otp\":\"123456\",\"attempts\":0}";
        when(valueOps.get(anyString())).thenReturn(storedJson);
        when(stringRedisTemplate.delete(anyString())).thenReturn(true);

        // ACT
        boolean result = otpService.verifyOtp(PHONE, TENANT_ID, "123456");

        // ASSERT
        assertThat(result).isTrue();
        verify(stringRedisTemplate).delete(anyString());
    }

    @Test
    @DisplayName("verifyOtp: throws OtpInvalidException and increments attempts when OTP is wrong")
    void verifyOtp_throwsOtpInvalid_andIncrementsAttempts_whenOtpWrong() {
        // ARRANGE
        String storedJson = "{\"otp\":\"123456\",\"attempts\":0}";
        when(valueOps.get(anyString())).thenReturn(storedJson);
        when(stringRedisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(200L);

        // ACT + ASSERT
        assertThatThrownBy(() -> otpService.verifyOtp(PHONE, TENANT_ID, "999999"))
                .isInstanceOf(OtpInvalidException.class);

        // attempts should be persisted as 1 — set() called with updated JSON
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(anyString(), valueCaptor.capture(), anyLong(), any(TimeUnit.class));
        assertThat(valueCaptor.getValue()).contains("\"attempts\":1");
    }

    @Test
    @DisplayName("verifyOtp: deletes key after max attempts exceeded")
    void verifyOtp_deletesKey_whenMaxAttemptsExceeded() {
        // ARRANGE — already at 2 attempts (3rd attempt will trigger deletion)
        String storedJson = "{\"otp\":\"123456\",\"attempts\":2}";
        when(valueOps.get(anyString())).thenReturn(storedJson);

        // ACT + ASSERT
        assertThatThrownBy(() -> otpService.verifyOtp(PHONE, TENANT_ID, "999999"))
                .isInstanceOf(OtpInvalidException.class);

        // Key must be deleted, not updated
        verify(stringRedisTemplate).delete(anyString());
        verify(valueOps, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("verifyOtp: throws OtpExpiredException when key is not in Redis")
    void verifyOtp_throwsOtpExpired_whenKeyNotFound() {
        // ARRANGE — Redis returns null (key expired or never set)
        when(valueOps.get(anyString())).thenReturn(null);

        // ACT + ASSERT
        assertThatThrownBy(() -> otpService.verifyOtp(PHONE, TENANT_ID, "123456"))
                .isInstanceOf(OtpExpiredException.class);
    }
}
