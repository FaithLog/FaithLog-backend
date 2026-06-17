package com.faithlog.user.application;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.global.security.JwtProvider;
import com.faithlog.global.security.JwtProvider.IssuedTokens;
import com.faithlog.user.domain.User;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import com.faithlog.user.presentation.dto.LoginResponse;
import com.faithlog.user.presentation.dto.SignupResponse;
import com.faithlog.user.presentation.dto.UserMeResponse;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;

	public AuthService(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		JwtProvider jwtProvider
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtProvider = jwtProvider;
	}

	@Transactional
	public SignupResponse signup(SignupCommand command) {
		if (userRepository.existsByEmail(command.email())) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 가입된 이메일입니다.");
		}

		User user = User.create(command.name(), command.email(), passwordEncoder.encode(command.password()));
		User savedUser = userRepository.save(user);
		return SignupResponse.from(savedUser);
	}

	@Transactional
	public LoginResponse login(LoginCommand command) {
		User user = userRepository.findByEmail(command.email())
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));

		if (!user.isActive() || !passwordEncoder.matches(command.password(), user.passwordHash())) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
		}

		Instant loginAt = Instant.now();
		user.updateLastLoginAt(loginAt);
		IssuedTokens tokens = jwtProvider.issueTokens(user);
		return LoginResponse.of(UserMeResponse.from(user), tokens);
	}

	@Transactional(readOnly = true)
	public UserMeResponse getCurrentUser(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		return UserMeResponse.from(user);
	}
}
