package com.faithlog.support;

import com.faithlog.user.service.port.EmailSenderPort;
import com.faithlog.user.service.port.EmailDispatchQueuePort;
import com.faithlog.user.service.port.EmailDispatchStore;
import com.faithlog.user.service.port.OneTimeTokenGenerator;
import com.faithlog.user.service.port.VerificationCodeGenerator;
import com.faithlog.user.service.policy.EmailVerificationPolicy;
import com.faithlog.user.support.InMemoryEmailVerificationStore;
import com.faithlog.user.support.InMemoryEmailDispatchStore;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class EmailVerificationTestConfig {

	@Bean
	InMemoryEmailVerificationStore testEmailVerificationStore() {
		return new InMemoryEmailVerificationStore();
	}

	@Bean
	EmailSenderPort testEmailSenderPort() {
		return (deliveryId, purpose, email, code, ttl) -> {
		};
	}

	@Bean
	EmailDispatchQueuePort testEmailDispatchQueuePort() {
		return command -> {
		};
	}

	@Bean
	EmailDispatchStore testEmailDispatchStore() {
		return new InMemoryEmailDispatchStore();
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
