package com.faithlog.user.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.ChangeMyPasswordCommand;
import com.faithlog.user.service.port.RefreshTokenStore;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatedPasswordChangeCommandService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenStore refreshTokenStore;

	public AuthenticatedPasswordChangeCommandService(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		RefreshTokenStore refreshTokenStore
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenStore = refreshTokenStore;
	}

	@Transactional
	public void changePassword(ChangeMyPasswordCommand command) {
		User user = userRepository.findByIdForUpdate(command.userId())
			.filter(User::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));

		if (!passwordEncoder.matches(command.currentPassword(), user.passwordHash())) {
			throw new BusinessException(ErrorCode.AUTH_CURRENT_PASSWORD_MISMATCH);
		}
		if (passwordEncoder.matches(command.newPassword(), user.passwordHash())) {
			throw new BusinessException(ErrorCode.AUTH_PASSWORD_CHANGE_SAME_PASSWORD);
		}

		user.changePassword(passwordEncoder.encode(command.newPassword()));
		refreshTokenStore.deleteAllSessions(user.id());
	}
}
