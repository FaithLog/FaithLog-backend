package com.faithlog.user.application;

import com.faithlog.campus.application.port.CampusMemberRepositoryPort;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.notification.domain.UserFcmToken;
import com.faithlog.notification.infrastructure.jpa.UserFcmTokenRepository;
import com.faithlog.user.application.port.AccessTokenBlacklistStore;
import com.faithlog.user.application.port.RefreshTokenStore;
import com.faithlog.user.domain.User;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

	private static final String DELETE_CONFIRM_TEXT = "회원탈퇴";
	private static final String DELETED_USER_NAME = "탈퇴한 사용자";

	private final UserRepository userRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final UserFcmTokenRepository userFcmTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenStore refreshTokenStore;
	private final AccessTokenBlacklistStore accessTokenBlacklistStore;

	public UserAccountService(
		UserRepository userRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		UserFcmTokenRepository userFcmTokenRepository,
		PasswordEncoder passwordEncoder,
		RefreshTokenStore refreshTokenStore,
		AccessTokenBlacklistStore accessTokenBlacklistStore
	) {
		this.userRepository = userRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userFcmTokenRepository = userFcmTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenStore = refreshTokenStore;
		this.accessTokenBlacklistStore = accessTokenBlacklistStore;
	}

	@Transactional
	public DeleteMyAccountResult deleteMyAccount(DeleteMyAccountCommand command) {
		if (command.userId() == null || command.sessionId() == null || command.accessJti() == null) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		User user = userRepository.findById(command.userId())
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.isActive() || user.deletedAt() != null) {
			throw new BusinessException(ErrorCode.USER_ALREADY_DELETED);
		}
		if (!passwordEncoder.matches(command.password(), user.passwordHash())) {
			throw new BusinessException(ErrorCode.USER_DELETE_PASSWORD_MISMATCH);
		}
		if (!DELETE_CONFIRM_TEXT.equals(command.confirmText())) {
			throw new BusinessException(ErrorCode.USER_DELETE_CONFIRM_TEXT_INVALID);
		}

		Instant deletedAt = Instant.now();
		deactivateCampusMemberships(user.id());
		deactivateFcmTokens(user.id());
		refreshTokenStore.deleteAllSessions(user.id());
		accessTokenBlacklistStore.blacklist(command.accessJti(), remainingAccessTokenTtl(command.accessTokenExpiresAt()));
		user.deleteAccount(
			anonymizedEmail(user.id()),
			DELETED_USER_NAME,
			passwordEncoder.encode(UUID.randomUUID().toString()),
			deletedAt
		);

		return new DeleteMyAccountResult(deletedAt);
	}

	private void deactivateCampusMemberships(Long userId) {
		for (CampusMember member : campusMemberRepository.findByUserIdOrderByIdAsc(userId)) {
			member.deactivate();
		}
	}

	private void deactivateFcmTokens(Long userId) {
		for (UserFcmToken token : userFcmTokenRepository.findByUserIdAndIsActiveTrue(userId)) {
			token.deactivate();
		}
	}

	private Duration remainingAccessTokenTtl(Instant accessTokenExpiresAt) {
		long remainingSeconds = Duration.between(Instant.now(), accessTokenExpiresAt).getSeconds();
		return Duration.ofSeconds(Math.max(0, remainingSeconds) + 60);
	}

	private String anonymizedEmail(Long userId) {
		return "deleted_user_%d@deleted.faithlog.local".formatted(userId);
	}
}
