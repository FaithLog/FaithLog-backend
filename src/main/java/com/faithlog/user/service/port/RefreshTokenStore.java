package com.faithlog.user.service.port;

import java.time.Duration;

public interface RefreshTokenStore {

	void saveCurrent(Long userId, String sessionId, String refreshJti, Duration ttl);

	RefreshTokenRotationResult rotate(
		Long userId,
		String sessionId,
		String expectedRefreshJti,
		String newRefreshJti,
		Duration ttl
	);

	void deleteSession(Long userId, String sessionId);

	default void deleteAllSessions(Long userId) {
	}
}
