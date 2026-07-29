package com.faithlog.user.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.user.service.EmailVerificationPurpose;
import com.faithlog.user.service.port.EmailDispatchQueueException;
import com.faithlog.user.service.port.EmailDispatchStore.EmailDispatchPayload;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmEmailDispatchCipherTest {

	private static final String KEY = Base64.getEncoder().encodeToString(
		"0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
	);

	@Test
	void encrypts_recipient_and_code_and_round_trips_the_payload() {
		AesGcmEmailDispatchCipher cipher = new AesGcmEmailDispatchCipher(KEY, new ObjectMapper());
		EmailDispatchPayload payload = new EmailDispatchPayload(
			EmailVerificationPurpose.PASSWORD_RESET,
			"private@example.com",
			"123456",
			300,
			true
		);

		String encrypted = cipher.encrypt(payload);

		assertThat(encrypted).doesNotContain("private@example.com", "123456");
		assertThat(cipher.decrypt(encrypted)).isEqualTo(payload);
		assertThat(cipher.fingerprint("opaque-token"))
			.hasSize(64)
			.doesNotContain("opaque-token");
	}

	@Test
	void rejects_invalid_keys_and_tampered_ciphertext() {
		assertThatThrownBy(() -> new AesGcmEmailDispatchCipher("not-base64", new ObjectMapper()))
			.isInstanceOf(EmailDispatchQueueException.class);
		assertThatThrownBy(() -> new AesGcmEmailDispatchCipher(
			Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8)),
			new ObjectMapper()
		)).isInstanceOf(EmailDispatchQueueException.class);

		AesGcmEmailDispatchCipher cipher = new AesGcmEmailDispatchCipher(KEY, new ObjectMapper());
		assertThatThrownBy(() -> cipher.decrypt("tampered"))
			.isInstanceOf(EmailDispatchQueueException.class);
	}
}
