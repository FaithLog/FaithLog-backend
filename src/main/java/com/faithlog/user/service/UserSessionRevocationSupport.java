package com.faithlog.user.service;

import com.faithlog.user.service.port.AccessTokenBlacklistStore;
import com.faithlog.user.service.port.RefreshTokenStore;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
class UserSessionRevocationSupport {

	private final RefreshTokenStore refreshTokenStore;
	private final AccessTokenBlacklistStore accessTokenBlacklistStore;

	UserSessionRevocationSupport(
		RefreshTokenStore refreshTokenStore,
		AccessTokenBlacklistStore accessTokenBlacklistStore
	) {
		this.refreshTokenStore = refreshTokenStore;
		this.accessTokenBlacklistStore = accessTokenBlacklistStore;
	}

	void revokeCurrentSession(Long userId, String sessionId, String accessJti, Instant accessTokenExpiresAt) {
		accessTokenBlacklistStore.blacklist(accessJti, remainingAccessTokenTtl(accessTokenExpiresAt));
		refreshTokenStore.deleteSession(userId, sessionId);
	}

	void revokeAllSessions(Long userId, String accessJti, Instant accessTokenExpiresAt) {
		refreshTokenStore.deleteAllSessions(userId);
		accessTokenBlacklistStore.blacklist(accessJti, remainingAccessTokenTtl(accessTokenExpiresAt));
	}

	private Duration remainingAccessTokenTtl(Instant accessTokenExpiresAt) {
		long remainingSeconds = Duration.between(Instant.now(), accessTokenExpiresAt).getSeconds();
		return Duration.ofSeconds(Math.max(0, remainingSeconds) + 60);
	}
}
