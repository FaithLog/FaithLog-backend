package com.faithlog.user.infrastructure.config;

import com.faithlog.user.infrastructure.redis.HmacVerificationSecretHasher;
import com.faithlog.user.service.policy.EmailVerificationPolicy;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class EmailVerificationConfiguration {

	@Bean
	HmacVerificationSecretHasher emailVerificationHmacHasher(
		@Value("${faithlog.auth.verification-hmac-secret:}") String secret,
		@Value("${faithlog.auth.email-verification-required:false}") boolean emailVerificationRequired
	) {
		HmacVerificationSecretHasher hasher = new HmacVerificationSecretHasher(secret);
		if (emailVerificationRequired && !hasher.isConfigured()) {
			throw new IllegalStateException(
				"AUTH_VERIFICATION_HMAC_SECRET must be configured when email verification is required"
			);
		}
		return hasher;
	}

	@Bean
	EmailVerificationPolicy emailVerificationPolicy() {
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
