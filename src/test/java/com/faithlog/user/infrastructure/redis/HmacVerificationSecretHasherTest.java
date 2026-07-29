package com.faithlog.user.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HmacVerificationSecretHasherTest {

	@Test
	void hashes_are_deterministic_context_bound_and_do_not_contain_the_secret_value() {
		HmacVerificationSecretHasher hasher = new HmacVerificationSecretHasher(
			"test-only-verification-hmac-secret"
		);

		String first = hasher.hash("challenge-code", "123456");
		String second = hasher.hash("challenge-code", "123456");
		String anotherContext = hasher.hash("grant-token", "123456");

		assertThat(first)
			.isEqualTo(second)
			.hasSize(64)
			.matches("[0-9a-f]{64}")
			.doesNotContain("123456");
		assertThat(anotherContext).isNotEqualTo(first);
	}

	@Test
	void missing_runtime_secret_fails_closed_without_echoing_input() {
		HmacVerificationSecretHasher hasher = new HmacVerificationSecretHasher("");

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> hasher.hash("challenge-code", "123456"))
			.isInstanceOf(com.faithlog.user.service.port.EmailVerificationStoreException.class)
			.hasMessage("Email verification store is unavailable")
			.hasMessageNotContaining("123456");
	}
}
