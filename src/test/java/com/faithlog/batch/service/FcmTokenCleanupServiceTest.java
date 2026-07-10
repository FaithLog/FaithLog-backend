package com.faithlog.batch.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.notification.service.result.FcmTokenResult;
import com.faithlog.notification.service.FcmTokenService;
import com.faithlog.notification.service.command.RegisterFcmTokenCommand;
import com.faithlog.notification.domain.type.DeviceType;
import com.faithlog.notification.domain.entity.UserFcmToken;
import com.faithlog.notification.infrastructure.repository.UserFcmTokenRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FcmTokenCleanupServiceTest {

	@Autowired
	private FcmTokenCleanupService fcmTokenCleanupService;

	@Autowired
	private FcmTokenService fcmTokenService;

	@Autowired
	private UserFcmTokenRepository userFcmTokenRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void deactivateStaleTokens_deactivates_active_tokens_older_than_ninety_days() {
		User user = userRepository.save(User.create("토큰정리", "batch-fcm@example.com", "{noop}password"));
		FcmTokenResult fresh = register(user, "fresh-token", "fresh-client");
		FcmTokenResult stale = register(user, "stale-token", "stale-client");
		UserFcmToken staleToken = userFcmTokenRepository.findById(stale.id()).orElseThrow();
		ReflectionTestUtils.setField(staleToken, "lastSeenAt", Instant.parse("2026-03-15T00:00:00Z"));
		ReflectionTestUtils.setField(staleToken, "lastRefreshedAt", Instant.parse("2026-03-15T00:00:00Z"));

		int deactivated = fcmTokenCleanupService.deactivateStaleTokens(Instant.parse("2026-06-21T00:00:00Z"));

		assertThat(deactivated).isEqualTo(1);
		assertThat(userFcmTokenRepository.findById(fresh.id())).get().extracting(UserFcmToken::isActive).isEqualTo(true);
		assertThat(userFcmTokenRepository.findById(stale.id())).get().satisfies(token -> {
			assertThat(token.isActive()).isFalse();
			assertThat(token.deactivatedAt()).isNotNull();
		});
	}

	@Test
	void deactivateStaleTokens_treats_either_seen_or_refreshed_older_than_ninety_days_as_stale() {
		User user = userRepository.save(User.create("토큰정리", "batch-fcm-partial@example.com", "{noop}password"));
		FcmTokenResult token = register(user, "partial-stale-token", "partial-stale-client");
		UserFcmToken staleToken = userFcmTokenRepository.findById(token.id()).orElseThrow();
		ReflectionTestUtils.setField(staleToken, "lastSeenAt", Instant.parse("2026-03-15T00:00:00Z"));
		ReflectionTestUtils.setField(staleToken, "lastRefreshedAt", Instant.parse("2026-06-20T00:00:00Z"));

		int deactivated = fcmTokenCleanupService.deactivateStaleTokens(Instant.parse("2026-06-21T00:00:00Z"));

		assertThat(deactivated).isEqualTo(1);
		assertThat(userFcmTokenRepository.findById(token.id())).get().extracting(UserFcmToken::isActive).isEqualTo(false);
	}

	private FcmTokenResult register(User user, String token, String clientInstanceId) {
		return fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			token,
			clientInstanceId,
			DeviceType.IOS,
			"1.0.0"
		));
	}
}
