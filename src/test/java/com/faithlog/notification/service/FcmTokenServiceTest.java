package com.faithlog.notification.service;

import com.faithlog.notification.service.command.RegisterFcmTokenCommand;
import com.faithlog.notification.service.result.FcmTokenResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.notification.domain.type.DeviceType;
import com.faithlog.notification.domain.entity.UserFcmToken;
import com.faithlog.notification.infrastructure.repository.UserFcmTokenRepository;
import com.faithlog.user.service.port.CurrentDeviceFcmTokenDeactivationCommand;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FcmTokenServiceTest {

	@Autowired
	private FcmTokenService fcmTokenService;

	@Autowired
	private UserFcmTokenRepository userFcmTokenRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void registerToken_upserts_same_user_token_and_refreshes_metadata() {
		User user = saveUser("fcm-upsert@example.com");
		FcmTokenResult created = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"token-a",
			"client-a",
			DeviceType.IOS,
			"1.0.0"
		));

		FcmTokenResult refreshed = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"token-a",
			"client-a",
			DeviceType.IOS,
			"1.1.0"
		));

		assertThat(refreshed.id()).isEqualTo(created.id());
		assertThat(refreshed.appVersion()).isEqualTo("1.1.0");
		assertThat(refreshed.isActive()).isTrue();
		assertThat(refreshed.lastSeenAt()).isAfterOrEqualTo(created.lastSeenAt());
		assertThat(refreshed.lastRefreshedAt()).isAfterOrEqualTo(created.lastRefreshedAt());
		assertThat(userFcmTokenRepository.findAll()).hasSize(1);
	}

	@Test
	void registerToken_deactivates_previous_active_token_for_same_client_instance() {
		User user = saveUser("fcm-client-change@example.com");
		FcmTokenResult first = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"token-before",
			"client-same",
			DeviceType.ANDROID,
			"1.0.0"
		));

		FcmTokenResult second = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"token-after",
			"client-same",
			DeviceType.ANDROID,
			"1.0.1"
		));

		UserFcmToken previous = userFcmTokenRepository.findById(first.id()).orElseThrow();
		UserFcmToken current = userFcmTokenRepository.findById(second.id()).orElseThrow();
		assertThat(previous.isActive()).isFalse();
		assertThat(previous.deactivatedAt()).isNotNull();
		assertThat(current.isActive()).isTrue();
		assertThat(current.token()).isEqualTo("token-after");
	}

	@Test
	void registerToken_deactivates_previous_active_owner_and_creates_current_owner_for_same_token() {
		User previousUser = saveUser("fcm-previous-owner@example.com");
		User currentUser = saveUser("fcm-current-owner@example.com");
		FcmTokenResult previous = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			previousUser.id(),
			"shared-token",
			"client-old",
			DeviceType.WEB,
			"1.0.0"
		));

		FcmTokenResult current = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			currentUser.id(),
			"shared-token",
			"client-new",
			DeviceType.WEB,
			"1.0.1"
		));

		assertThat(current.id()).isNotEqualTo(previous.id());
		UserFcmToken previousOwnership = userFcmTokenRepository.findById(previous.id()).orElseThrow();
		UserFcmToken currentOwnership = userFcmTokenRepository.findById(current.id()).orElseThrow();
		assertThat(previousOwnership.userId()).isEqualTo(previousUser.id());
		assertThat(previousOwnership.clientInstanceId()).isEqualTo("client-old");
		assertThat(previousOwnership.isActive()).isFalse();
		assertThat(previousOwnership.deactivatedAt()).isNotNull();
		assertThat(currentOwnership.userId()).isEqualTo(currentUser.id());
		assertThat(currentOwnership.clientInstanceId()).isEqualTo("client-new");
		assertThat(currentOwnership.isActive()).isTrue();
		assertThat(userFcmTokenRepository.findActiveSendableTokens(previousUser.id())).isEmpty();
		assertThat(userFcmTokenRepository.findActiveSendableTokens(currentUser.id()))
			.extracting(UserFcmToken::id)
			.containsExactly(current.id());
	}

	@Test
	void registerToken_keeps_only_one_active_token_for_same_user_and_client_instance() {
		User user = saveUser("fcm-single-active-client@example.com");
		FcmTokenResult first = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"client-token-1",
			"client-repeat",
			DeviceType.IOS,
			"1.0.0"
		));
		FcmTokenResult second = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"client-token-2",
			"client-repeat",
			DeviceType.IOS,
			"1.0.1"
		));
		FcmTokenResult refreshed = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"client-token-2",
			"client-repeat",
			DeviceType.IOS,
			"1.0.2"
		));

		assertThat(refreshed.id()).isEqualTo(second.id());
		assertThat(userFcmTokenRepository.findById(first.id())).get().extracting(UserFcmToken::isActive).isEqualTo(false);
		assertThat(userFcmTokenRepository.findByUserIdAndClientInstanceIdAndIsActiveTrue(user.id(), "client-repeat"))
			.extracting(UserFcmToken::id)
			.containsExactly(second.id());
	}

	@Test
	void deactivateToken_requires_owner_and_soft_deletes_token() {
		User owner = saveUser("fcm-owner@example.com");
		User other = saveUser("fcm-other@example.com");
		FcmTokenResult token = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			owner.id(),
			"owner-token",
			"owner-client",
			DeviceType.IOS,
			"1.0.0"
		));

		assertThatThrownBy(() -> fcmTokenService.deactivateToken(other.id(), token.id()))
			.isInstanceOf(BusinessException.class)
			.hasMessage("FCM 토큰을 찾을 수 없습니다.");

		fcmTokenService.deactivateToken(owner.id(), token.id());

		UserFcmToken deactivated = userFcmTokenRepository.findById(token.id()).orElseThrow();
		assertThat(deactivated.isActive()).isFalse();
		assertThat(deactivated.deactivatedAt()).isNotNull();
	}

	@Test
	void deactivateCurrentDevice_deletes_active_rows_by_token_or_client_instance() {
		User user = saveUser("fcm-logout@example.com");
		FcmTokenResult token = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"logout-token",
			"logout-client",
			DeviceType.ANDROID,
			"1.0.0"
		));

		fcmTokenService.deactivateCurrentDevice(new CurrentDeviceFcmTokenDeactivationCommand(
			user.id(),
			null,
			"logout-token"
		));

		assertThat(userFcmTokenRepository.findById(token.id())).isEmpty();

		FcmTokenResult second = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"logout-token-2",
			"logout-client-2",
			DeviceType.ANDROID,
			"1.0.0"
		));
		fcmTokenService.deactivateCurrentDevice(new CurrentDeviceFcmTokenDeactivationCommand(
			user.id(),
			"logout-client-2",
			null
		));

		assertThat(userFcmTokenRepository.findById(second.id())).isEmpty();
	}

	@Test
	void deactivateCurrentDevice_does_not_delete_other_active_rows() {
		User user = saveUser("fcm-logout-preserve@example.com");
		FcmTokenResult current = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"logout-current-token",
			"logout-current-client",
			DeviceType.ANDROID,
			"1.0.0"
		));
		FcmTokenResult other = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"logout-other-token",
			"logout-other-client",
			DeviceType.ANDROID,
			"1.0.0"
		));

		fcmTokenService.deactivateCurrentDevice(new CurrentDeviceFcmTokenDeactivationCommand(
			user.id(),
			"logout-current-client",
			"logout-current-token"
		));

		assertThat(userFcmTokenRepository.findById(current.id())).isEmpty();
		assertThat(userFcmTokenRepository.findById(other.id())).isPresent();
	}

	@Test
	void findActiveSendableTokens_excludes_tokens_stale_for_ninety_days() {
		User user = saveUser("fcm-stale@example.com");
		FcmTokenResult fresh = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"fresh-token",
			"fresh-client",
			DeviceType.IOS,
			"1.0.0"
		));
		FcmTokenResult stale = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"stale-token",
			"stale-client",
			DeviceType.IOS,
			"1.0.0"
		));
		UserFcmToken staleToken = userFcmTokenRepository.findById(stale.id()).orElseThrow();
		ReflectionTestUtils.setField(staleToken, "lastSeenAt", staleToken.lastSeenAt().minus(java.time.Duration.ofDays(91)));
		ReflectionTestUtils.setField(staleToken, "lastRefreshedAt", staleToken.lastRefreshedAt().minus(java.time.Duration.ofDays(91)));

		List<UserFcmToken> sendableTokens = userFcmTokenRepository.findActiveSendableTokens(user.id());

		assertThat(sendableTokens).extracting(UserFcmToken::id).containsExactly(fresh.id());
	}

	private User saveUser(String email) {
		return userRepository.save(User.create(email.substring(0, email.indexOf('@')), email, "{noop}password"));
	}
}
