package com.faithlog.user.application;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.global.security.JwtProvider;
import com.faithlog.global.security.JwtProvider.IssuedTokens;
import com.faithlog.user.application.port.AccessTokenBlacklistStore;
import com.faithlog.user.application.port.CurrentDeviceFcmTokenDeactivationCommand;
import com.faithlog.user.application.port.CurrentDeviceFcmTokenDeactivationPort;
import com.faithlog.user.application.port.RefreshTokenStore;
import com.faithlog.user.domain.User;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;
	private final RefreshTokenStore refreshTokenStore;
	private final AccessTokenBlacklistStore accessTokenBlacklistStore;
	private final CurrentDeviceFcmTokenDeactivationPort fcmTokenDeactivationPort;

	public AuthService(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		JwtProvider jwtProvider,
		RefreshTokenStore refreshTokenStore,
		AccessTokenBlacklistStore accessTokenBlacklistStore,
		CurrentDeviceFcmTokenDeactivationPort fcmTokenDeactivationPort
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtProvider = jwtProvider;
		this.refreshTokenStore = refreshTokenStore;
		this.accessTokenBlacklistStore = accessTokenBlacklistStore;
		this.fcmTokenDeactivationPort = fcmTokenDeactivationPort;
	}

	@Transactional
	public SignupResult signup(SignupCommand command) {
		if (userRepository.existsByEmail(command.email())) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}

		User user = User.create(command.name(), command.email(), passwordEncoder.encode(command.password()));
		User savedUser = userRepository.save(user);
		return SignupResult.from(savedUser);
	}

	@Transactional
	public LoginResult login(LoginCommand command) {
		User user = userRepository.findByEmail(command.email())
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

		if (!user.isActive() || !passwordEncoder.matches(command.password(), user.passwordHash())) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
		}

		Instant loginAt = Instant.now();
		user.updateLastLoginAt(loginAt);
		IssuedTokens tokens = jwtProvider.issueTokens(user);
		saveRefreshToken(tokens);
		return LoginResult.of(UserMeResult.from(user), tokens);
	}

	@Transactional
	public TokenResult refresh(RefreshCommand command) {
		Claims refreshClaims = parseRefreshToken(command.refreshToken());
		Long userId = refreshClaims.get("userId", Long.class);
		String sessionId = refreshClaims.get("sessionId", String.class);
		String refreshJti = refreshClaims.get("refreshJti", String.class);
		if (userId == null || sessionId == null || refreshJti == null) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}

		if (!refreshTokenStore.matchesCurrent(userId, sessionId, refreshJti)) {
			refreshTokenStore.deleteSession(userId, sessionId);
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}

		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.isActive()) {
			refreshTokenStore.deleteSession(userId, sessionId);
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}

		IssuedTokens tokens = jwtProvider.issueTokens(user, sessionId);
		saveRefreshToken(tokens);
		return TokenResult.from(tokens);
	}

	@Transactional
	public void logout(LogoutCommand command) {
		if (command.accessJti() == null || command.sessionId() == null || command.userId() == null) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}

		accessTokenBlacklistStore.blacklist(command.accessJti(), remainingAccessTokenTtl(command.accessTokenExpiresAt()));
		refreshTokenStore.deleteSession(command.userId(), command.sessionId());
		deactivateFcmTokenIfRequested(command);
	}

	@Transactional(readOnly = true)
	public UserMeResult getCurrentUser(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return UserMeResult.from(user);
	}

	private Claims parseRefreshToken(String refreshToken) {
		try {
			return jwtProvider.parseRefreshToken(refreshToken);
		} catch (JwtException | IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
	}

	private void saveRefreshToken(IssuedTokens tokens) {
		refreshTokenStore.saveCurrent(
			tokens.userId(),
			tokens.sessionId(),
			tokens.refreshJti(),
			Duration.ofSeconds(tokens.refreshTokenExpiresIn())
		);
	}

	private Duration remainingAccessTokenTtl(Instant accessTokenExpiresAt) {
		long remainingSeconds = Duration.between(Instant.now(), accessTokenExpiresAt).getSeconds();
		return Duration.ofSeconds(Math.max(0, remainingSeconds) + 60);
	}

	private void deactivateFcmTokenIfRequested(LogoutCommand command) {
		if (command.clientInstanceId() == null && command.fcmToken() == null) {
			return;
		}
		fcmTokenDeactivationPort.deactivateCurrentDevice(new CurrentDeviceFcmTokenDeactivationCommand(
			command.userId(),
			command.clientInstanceId(),
			command.fcmToken()
		));
	}
}
