package com.faithlog.user.infrastructure.redis;

final class AuthRedisKeys {

	private static final String REFRESH_PREFIX = "auth:refresh:";
	private static final String REVOKED_SESSION_PREFIX = "auth:session:revoked:";

	private AuthRedisKeys() {
	}

	static String refresh(Long userId, String sessionId) {
		return REFRESH_PREFIX + userId + ":" + sessionId;
	}

	static String revokedSession(Long userId, String sessionId) {
		return REVOKED_SESSION_PREFIX + userId + ":" + sessionId;
	}

	static String allRefreshSessions(Long userId) {
		return REFRESH_PREFIX + userId + ":*";
	}
}
