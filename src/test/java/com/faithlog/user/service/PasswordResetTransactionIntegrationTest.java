package com.faithlog.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.CompletePasswordResetCommand;
import com.faithlog.user.support.InMemoryEmailVerificationStore;
import com.faithlog.user.support.InMemoryRefreshTokenStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PasswordResetTransactionIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private PasswordResetCommandService passwordResetCommandService;

	@Autowired
	private InMemoryEmailVerificationStore verificationStore;

	@Autowired
	private FailingDeleteRefreshTokenStore refreshTokenStore;

	@Test
	void refresh_session_delete_failure_rolls_back_password_and_token_version() {
		String email = "reset-rollback-" + UUID.randomUUID() + "@example.com";
		User user = userRepository.saveAndFlush(User.create(
			"롤백",
			email,
			passwordEncoder.encode("old-password")
		));
		verificationStore.putPasswordResetGrant("rollback-grant", user.id());
		refreshTokenStore.failDeleteAll = true;

		assertThatThrownBy(() -> passwordResetCommandService.complete(
			new CompletePasswordResetCommand("rollback-grant", "new-password")
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("test-only refresh session deletion failure");

		User unchanged = userRepository.findById(user.id()).orElseThrow();
		assertThat(passwordEncoder.matches("old-password", unchanged.passwordHash())).isTrue();
		assertThat(unchanged.tokenVersion()).isZero();
	}

	@TestConfiguration
	static class FailureConfig {

		@Bean
		@Primary
		FailingDeleteRefreshTokenStore failingDeleteRefreshTokenStore() {
			return new FailingDeleteRefreshTokenStore();
		}
	}

	static class FailingDeleteRefreshTokenStore extends InMemoryRefreshTokenStore {
		private boolean failDeleteAll;

		@Override
		public synchronized void deleteAllSessions(Long userId) {
			if (failDeleteAll) {
				throw new IllegalStateException("test-only refresh session deletion failure");
			}
			super.deleteAllSessions(userId);
		}
	}
}
