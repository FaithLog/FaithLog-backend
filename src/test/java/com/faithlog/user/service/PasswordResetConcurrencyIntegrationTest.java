package com.faithlog.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.CompletePasswordResetCommand;
import com.faithlog.user.service.command.LoginCommand;
import com.faithlog.user.service.command.RefreshCommand;
import com.faithlog.user.support.InMemoryEmailVerificationStore;
import com.faithlog.user.support.InMemoryRefreshTokenStore;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class PasswordResetConcurrencyIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuthService authService;

	@Autowired
	private PasswordResetCommandService passwordResetCommandService;

	@Autowired
	private InMemoryEmailVerificationStore verificationStore;

	@Autowired
	private BlockingDeleteRefreshTokenStore refreshTokenStore;

	@Test
	void refresh_waiting_on_the_user_lock_is_rejected_after_password_reset_commits() throws Exception {
		String email = "reset-refresh-race-" + UUID.randomUUID() + "@example.com";
		User user = userRepository.saveAndFlush(User.create(
			"경쟁",
			email,
			passwordEncoder.encode("old-password")
		));
		String oldRefreshToken = authService.login(new LoginCommand(email, "old-password")).refreshToken();
		verificationStore.putPasswordResetGrant("race-grant", user.id());
		refreshTokenStore.pauseNextDeleteAll();

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<?> reset = executor.submit(() -> passwordResetCommandService.complete(
				new CompletePasswordResetCommand("race-grant", "new-password")
			));
			assertThat(refreshTokenStore.awaitDeleteAllStarted()).isTrue();

			Future<?> refresh = executor.submit(() -> authService.refresh(new RefreshCommand(oldRefreshToken)));
			Thread.sleep(150);
			assertThat(refresh.isDone()).isFalse();

			refreshTokenStore.allowDeleteAll();
			reset.get(5, TimeUnit.SECONDS);
			assertUnauthorized(refresh);
		}
	}

	@Test
	void the_same_reset_grant_has_exactly_one_successful_concurrent_completion() throws Exception {
		String email = "reset-complete-race-" + UUID.randomUUID() + "@example.com";
		User user = userRepository.saveAndFlush(User.create(
			"동시 완료",
			email,
			passwordEncoder.encode("old-password")
		));
		verificationStore.putPasswordResetGrant("single-use-grant", user.id());
		refreshTokenStore.allowDeleteAll();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<ErrorCode> first = executor.submit(completion(ready, start, "new-password-a"));
			Future<ErrorCode> second = executor.submit(completion(ready, start, "new-password-b"));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(java.util.Arrays.asList(
				first.get(5, TimeUnit.SECONDS),
				second.get(5, TimeUnit.SECONDS)
			))
				.containsExactlyInAnyOrder(null, ErrorCode.AUTH_PASSWORD_RESET_TOKEN_INVALID);
		}
		assertThat(verificationStore.resolvePasswordResetGrant("single-use-grant")).isEmpty();
		assertThat(userRepository.findById(user.id()).orElseThrow().tokenVersion()).isEqualTo(1L);
	}

	private Callable<ErrorCode> completion(CountDownLatch ready, CountDownLatch start, String password) {
		return () -> {
			ready.countDown();
			if (!start.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Concurrent password reset timed out");
			}
			try {
				passwordResetCommandService.complete(new CompletePasswordResetCommand(
					"single-use-grant",
					password
				));
				return null;
			} catch (BusinessException exception) {
				return exception.errorCode();
			}
		};
	}

	private void assertUnauthorized(Future<?> action) throws Exception {
		try {
			action.get(5, TimeUnit.SECONDS);
			throw new AssertionError("Expected authentication failure");
		} catch (ExecutionException exception) {
			assertThat(exception.getCause())
				.isInstanceOf(BusinessException.class)
				.extracting(cause -> ((BusinessException) cause).errorCode())
				.isEqualTo(ErrorCode.AUTH_UNAUTHORIZED);
		}
	}

	@TestConfiguration
	static class ConcurrencyConfig {

		@Bean
		@Primary
		BlockingDeleteRefreshTokenStore blockingDeleteRefreshTokenStore() {
			return new BlockingDeleteRefreshTokenStore();
		}
	}

	static class BlockingDeleteRefreshTokenStore extends InMemoryRefreshTokenStore {
		private CountDownLatch deleteAllStarted = new CountDownLatch(1);
		private CountDownLatch allowDeleteAll = new CountDownLatch(1);

		@Override
		public synchronized void deleteAllSessions(Long userId) {
			deleteAllStarted.countDown();
			try {
				if (!allowDeleteAll.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("test-only deleteAll timeout");
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
			super.deleteAllSessions(userId);
		}

		void pauseNextDeleteAll() {
			deleteAllStarted = new CountDownLatch(1);
			allowDeleteAll = new CountDownLatch(1);
		}

		boolean awaitDeleteAllStarted() throws InterruptedException {
			return deleteAllStarted.await(5, TimeUnit.SECONDS);
		}

		void allowDeleteAll() {
			allowDeleteAll.countDown();
		}
	}
}
