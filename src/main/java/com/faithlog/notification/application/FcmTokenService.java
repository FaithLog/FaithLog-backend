package com.faithlog.notification.application;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.notification.domain.UserFcmToken;
import com.faithlog.notification.infrastructure.jpa.UserFcmTokenRepository;
import com.faithlog.user.application.port.CurrentDeviceFcmTokenDeactivationCommand;
import com.faithlog.user.application.port.CurrentDeviceFcmTokenDeactivationPort;
import com.faithlog.user.domain.User;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FcmTokenService implements CurrentDeviceFcmTokenDeactivationPort {

	private final UserFcmTokenRepository userFcmTokenRepository;
	private final UserRepository userRepository;

	public FcmTokenService(UserFcmTokenRepository userFcmTokenRepository, UserRepository userRepository) {
		this.userFcmTokenRepository = userFcmTokenRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public FcmTokenResult registerToken(RegisterFcmTokenCommand command) {
		User requester = userRepository.findById(command.userId())
			.filter(User::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));

		boolean deactivated = deactivateOtherActiveTokensForClient(requester.id(), command.clientInstanceId(), command.token());
		deactivated = deactivateOtherActiveOwnersForToken(requester.id(), command.clientInstanceId(), command.token()) || deactivated;
		if (deactivated) {
			userFcmTokenRepository.flush();
		}

		UserFcmToken token = userFcmTokenRepository
			.findByUserIdAndClientInstanceIdAndTokenAndIsActiveTrue(
				requester.id(),
				command.clientInstanceId(),
				command.token()
			)
			.orElseGet(() -> userFcmTokenRepository.save(UserFcmToken.create(
				requester.id(),
				command.token(),
				command.clientInstanceId(),
				command.deviceType(),
				command.appVersion()
			)));
		token.refresh(
			requester.id(),
			command.clientInstanceId(),
			command.deviceType(),
			command.appVersion()
		);
		return FcmTokenResult.from(token);
	}

	@Transactional
	public void deactivateToken(Long userId, Long tokenId) {
		UserFcmToken token = userFcmTokenRepository.findByIdAndUserId(tokenId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_FCM_TOKEN_NOT_FOUND));
		token.deactivate();
	}

	@Override
	@Transactional
	public void deactivateCurrentDevice(CurrentDeviceFcmTokenDeactivationCommand command) {
		if (command.fcmToken() != null) {
			userFcmTokenRepository.findByUserIdAndTokenAndIsActiveTrue(command.userId(), command.fcmToken())
				.forEach(UserFcmToken::deactivate);
		}
		if (command.clientInstanceId() != null) {
			userFcmTokenRepository.findByUserIdAndClientInstanceIdAndIsActiveTrue(command.userId(), command.clientInstanceId())
				.forEach(UserFcmToken::deactivate);
		}
	}

	private boolean deactivateOtherActiveTokensForClient(Long userId, String clientInstanceId, String currentToken) {
		List<UserFcmToken> activeTokens = userFcmTokenRepository
			.findByUserIdAndClientInstanceIdAndIsActiveTrue(userId, clientInstanceId);
		return deactivateAll(activeTokens.stream()
			.filter(token -> !token.token().equals(currentToken))
			.toList());
	}

	private boolean deactivateOtherActiveOwnersForToken(Long userId, String clientInstanceId, String currentToken) {
		List<UserFcmToken> activeTokens = userFcmTokenRepository.findByTokenAndIsActiveTrue(currentToken);
		return deactivateAll(activeTokens.stream()
			.filter(token -> !token.userId().equals(userId) || !token.clientInstanceId().equals(clientInstanceId))
			.toList());
	}

	private boolean deactivateAll(List<UserFcmToken> tokens) {
		tokens.forEach(UserFcmToken::deactivate);
		return !tokens.isEmpty();
	}
}
