package com.faithlog.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.ChangeMyPasswordCommand;
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
class AuthenticatedPasswordChangeTransactionIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuthenticatedPasswordChangeCommandService service;

	@Autowired
	private FailingDeleteRefreshTokenStore refreshTokenStore;

	@Test
	void refresh_session_delete_failure_rolls_back_password_and_token_version() {
		User user = userRepository.saveAndFlush(User.create(
			"롤백",
			"password-change-rollback-" + UUID.randomUUID() + "@example.com",
			passwordEncoder.encode("old-password")
		));
		refreshTokenStore.failDeleteAll = true;

		assertThatThrownBy(() -> service.changePassword(
			new ChangeMyPasswordCommand(user.id(), "old-password", "new-password")
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
