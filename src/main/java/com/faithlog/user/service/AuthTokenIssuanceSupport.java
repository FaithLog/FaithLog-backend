package com.faithlog.user.service;

import com.faithlog.global.security.JwtProvider;
import com.faithlog.global.security.JwtProvider.IssuedTokens;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.service.port.RefreshTokenStore;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
class AuthTokenIssuanceSupport {

	private final JwtProvider jwtProvider;
	private final RefreshTokenStore refreshTokenStore;

	AuthTokenIssuanceSupport(JwtProvider jwtProvider, RefreshTokenStore refreshTokenStore) {
		this.jwtProvider = jwtProvider;
		this.refreshTokenStore = refreshTokenStore;
	}

	IssuedTokens issue(User user) {
		return save(jwtProvider.issueTokens(user));
	}

	IssuedTokens createRotationCandidate(User user, String sessionId) {
		return jwtProvider.issueTokens(user, sessionId);
	}

	private IssuedTokens save(IssuedTokens tokens) {
		refreshTokenStore.saveCurrent(
			tokens.userId(),
			tokens.sessionId(),
			tokens.refreshJti(),
			Duration.ofSeconds(tokens.refreshTokenExpiresIn())
		);
		return tokens;
	}
}
