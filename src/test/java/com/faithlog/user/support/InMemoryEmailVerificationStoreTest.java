package com.faithlog.user.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.user.service.EmailVerificationPurpose;
import com.faithlog.user.service.policy.EmailVerificationPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryEmailVerificationStoreTest {

	private static final EmailVerificationPolicy POLICY = new EmailVerificationPolicy(
		Duration.ofMinutes(5),
		Duration.ofSeconds(60),
		Duration.ofHours(1),
		Duration.ofMinutes(10),
		5,
		5
	);

	@Test
	void unknown_password_reset_subject_is_consumed_as_an_unusable_grant() {
		var store = new InMemoryEmailVerificationStore();
		store.issueChallenge(
			EmailVerificationPurpose.PASSWORD_RESET,
			"missing@example.com",
			"123456",
			POLICY
		);

		assertThat(store.confirmChallenge(
			EmailVerificationPurpose.PASSWORD_RESET,
			"missing@example.com",
			"123456",
			"reset-token",
			"missing",
			POLICY
		)).isEqualTo(com.faithlog.user.service.port.EmailVerificationStore.ChallengeVerificationResult.VERIFIED);
		assertThat(store.resolvePasswordResetGrant("reset-token").isEmpty()).isTrue();
		store.discardPasswordResetGrant("reset-token");
		assertThat(store.resolvePasswordResetGrant("reset-token")).isEmpty();
	}
}
