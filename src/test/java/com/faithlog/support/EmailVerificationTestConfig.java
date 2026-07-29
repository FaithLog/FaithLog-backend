package com.faithlog.support;

import com.faithlog.user.service.EmailVerificationPolicy;
import com.faithlog.user.service.EmailVerificationPurpose;
import com.faithlog.user.service.port.EmailSenderPort;
import com.faithlog.user.service.port.EmailVerificationStore;
import com.faithlog.user.service.port.OneTimeTokenGenerator;
import com.faithlog.user.service.port.VerificationCodeGenerator;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class EmailVerificationTestConfig {

	@Bean
	EmailVerificationStore testEmailVerificationStore() {
		return new EmailVerificationStore() {
			@Override
			public ChallengeIssueResult issueChallenge(
				EmailVerificationPurpose purpose,
				String email,
				String code,
				EmailVerificationPolicy policy
			) {
				return ChallengeIssueResult.ISSUED;
			}

			@Override
			public void cancelChallenge(EmailVerificationPurpose purpose, String email, String code) {
			}

			@Override
			public ChallengeVerificationResult confirmChallenge(
				EmailVerificationPurpose purpose,
				String email,
				String code,
				String grantToken,
				String grantSubject,
				EmailVerificationPolicy policy
			) {
				return ChallengeVerificationResult.VERIFIED;
			}

			@Override
			public boolean consumeSignupGrant(String email, String grantToken) {
				return true;
			}

			@Override
			public OptionalLong consumePasswordResetGrant(String grantToken) {
				return OptionalLong.empty();
			}
		};
	}

	@Bean
	EmailSenderPort testEmailSenderPort() {
		return (purpose, email, code, ttl) -> {
		};
	}

	@Bean
	VerificationCodeGenerator testVerificationCodeGenerator() {
		return () -> "123456";
	}

	@Bean
	OneTimeTokenGenerator testOneTimeTokenGenerator() {
		return () -> UUID.randomUUID().toString();
	}

	@Bean
	EmailVerificationPolicy testEmailVerificationPolicy() {
		return new EmailVerificationPolicy(
			Duration.ofMinutes(5),
			Duration.ofSeconds(60),
			Duration.ofHours(1),
			Duration.ofMinutes(10),
			5,
			5
		);
	}
}
