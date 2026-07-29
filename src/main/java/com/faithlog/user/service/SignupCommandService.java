package com.faithlog.user.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.SignupCommand;
import com.faithlog.user.service.port.EmailVerificationStore;
import com.faithlog.user.service.result.SignupResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupCommandService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailVerificationStore verificationStore;
	private final boolean emailVerificationRequired;

	public SignupCommandService(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		EmailVerificationStore verificationStore,
		@Value("${faithlog.auth.email-verification-required:false}") boolean emailVerificationRequired
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.verificationStore = verificationStore;
		this.emailVerificationRequired = emailVerificationRequired;
	}

	@Transactional
	public SignupResult signup(SignupCommand command) {
		String email = EmailNormalizer.normalize(command.email());
		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}
		consumeVerificationGrant(email, command.emailVerificationToken());

		User user = User.create(command.name(), email, passwordEncoder.encode(command.password()));
		User savedUser = userRepository.save(user);
		return SignupResult.from(savedUser);
	}

	private void consumeVerificationGrant(String email, String token) {
		if (token == null) {
			if (emailVerificationRequired) {
				throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_REQUIRED);
			}
			return;
		}
		if (token.isBlank() || !verificationStore.consumeSignupGrant(email, token)) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_TOKEN_INVALID);
		}
	}
}
