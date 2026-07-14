package com.faithlog.user.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.global.security.JwtProvider.IssuedTokens;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.LoginCommand;
import com.faithlog.user.service.result.LoginResult;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginCommandService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthTokenIssuanceSupport tokenIssuanceSupport;
	private final CampusMembershipQuerySupport campusMembershipQuerySupport;

	public LoginCommandService(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		AuthTokenIssuanceSupport tokenIssuanceSupport,
		CampusMembershipQuerySupport campusMembershipQuerySupport
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenIssuanceSupport = tokenIssuanceSupport;
		this.campusMembershipQuerySupport = campusMembershipQuerySupport;
	}

	@Transactional
	public LoginResult login(LoginCommand command) {
		User user = userRepository.findByEmailForUpdate(command.email())
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

		if (!user.isActive() || !passwordEncoder.matches(command.password(), user.passwordHash())) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
		}

		user.updateLastLoginAt(Instant.now());
		IssuedTokens tokens = tokenIssuanceSupport.issue(user);
		return LoginResult.of(campusMembershipQuerySupport.toUserMeResult(user), tokens);
	}
}
