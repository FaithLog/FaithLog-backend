package com.faithlog.user.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.CompletePasswordResetCommand;
import com.faithlog.user.service.port.EmailVerificationStore;
import com.faithlog.user.service.port.EmailVerificationStoreException;
import com.faithlog.user.service.port.RefreshTokenStore;
import java.util.OptionalLong;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetCommandService {

	private final UserRepository userRepository;
	private final EmailVerificationStore verificationStore;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenStore refreshTokenStore;

	public PasswordResetCommandService(
		UserRepository userRepository,
		EmailVerificationStore verificationStore,
		PasswordEncoder passwordEncoder,
		RefreshTokenStore refreshTokenStore
	) {
		this.userRepository = userRepository;
		this.verificationStore = verificationStore;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenStore = refreshTokenStore;
	}

	@Transactional
	public void complete(CompletePasswordResetCommand command) {
		long userId = resolveGrant(command.resetToken());
		User user = userRepository.findByIdForUpdate(userId)
			.filter(User::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_PASSWORD_RESET_TOKEN_INVALID));
		if (resolveGrant(command.resetToken()) != userId) {
			throw new BusinessException(ErrorCode.AUTH_PASSWORD_RESET_TOKEN_INVALID);
		}
		if (passwordEncoder.matches(command.newPassword(), user.passwordHash())) {
			throw new BusinessException(ErrorCode.AUTH_PASSWORD_RESET_SAME_PASSWORD);
		}
		consumeGrant(command.resetToken(), userId);

		user.changePassword(passwordEncoder.encode(command.newPassword()));
		refreshTokenStore.deleteAllSessions(user.id());
	}

	private long resolveGrant(String token) {
		OptionalLong userId;
		try {
			userId = verificationStore.resolvePasswordResetGrant(token);
		} catch (EmailVerificationStoreException exception) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_UNAVAILABLE);
		}
		if (userId.isEmpty()) {
			throw new BusinessException(ErrorCode.AUTH_PASSWORD_RESET_TOKEN_INVALID);
		}
		return userId.getAsLong();
	}

	private void consumeGrant(String token, long userId) {
		try {
			if (!verificationStore.consumePasswordResetGrant(token, userId)) {
				throw new BusinessException(ErrorCode.AUTH_PASSWORD_RESET_TOKEN_INVALID);
			}
		} catch (EmailVerificationStoreException exception) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_UNAVAILABLE);
		}
	}
}
