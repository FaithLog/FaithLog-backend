package com.faithlog.user.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.user.service.port.EmailVerificationStoreException;
import org.junit.jupiter.api.Test;

class EmailVerificationConfigurationTest {

	private final EmailVerificationConfiguration configuration = new EmailVerificationConfiguration();

	@Test
	void rollout_disabled_allows_blank_startup_but_operations_remain_fail_closed() {
		var hasher = configuration.emailVerificationHmacHasher("", false);

		assertThat(hasher.isConfigured()).isFalse();
		assertThatThrownBy(() -> hasher.hash("context", "value"))
			.isInstanceOf(EmailVerificationStoreException.class)
			.hasMessage("Email verification store is unavailable");
	}

	@Test
	void rollout_required_rejects_blank_secret_during_configuration() {
		assertThatThrownBy(() -> configuration.emailVerificationHmacHasher("", true))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("AUTH_VERIFICATION_HMAC_SECRET must be configured");
	}
}
