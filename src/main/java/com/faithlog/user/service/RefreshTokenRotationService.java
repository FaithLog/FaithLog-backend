package com.faithlog.user.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.global.security.JwtProvider;
import com.faithlog.global.security.JwtProvider.IssuedTokens;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.RefreshCommand;
import com.faithlog.user.service.port.RefreshTokenStore;
import com.faithlog.user.service.result.TokenResult;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenRotationService {

	private final UserRepository userRepository;
	private final JwtProvider jwtProvider;
	private final RefreshTokenStore refreshTokenStore;
	private final AuthTokenIssuanceSupport tokenIssuanceSupport;

	public RefreshTokenRotationService(
		UserRepository userRepository,
		JwtProvider jwtProvider,
		RefreshTokenStore refreshTokenStore,
		AuthTokenIssuanceSupport tokenIssuanceSupport
	) {
		this.userRepository = userRepository;
		this.jwtProvider = jwtProvider;
		this.refreshTokenStore = refreshTokenStore;
		this.tokenIssuanceSupport = tokenIssuanceSupport;
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

		IssuedTokens tokens = tokenIssuanceSupport.issue(user, sessionId);
		return TokenResult.from(tokens);
	}

	private Claims parseRefreshToken(String refreshToken) {
		try {
			return jwtProvider.parseRefreshToken(refreshToken);
		} catch (JwtException | IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
	}
}
