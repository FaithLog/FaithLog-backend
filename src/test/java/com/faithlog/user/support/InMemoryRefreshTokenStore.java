package com.faithlog.user.support;

import com.faithlog.user.service.port.RefreshTokenStore;
import com.faithlog.user.service.port.RefreshTokenRotationResult;
import com.faithlog.user.service.port.SessionRevocationStore;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRefreshTokenStore implements RefreshTokenStore, SessionRevocationStore {

	private final Map<String, String> currentRefreshJtiBySession = new ConcurrentHashMap<>();
	private final Map<String, Boolean> revokedSessions = new ConcurrentHashMap<>();

	@Override
	public synchronized void saveCurrent(Long userId, String sessionId, String refreshJti, Duration ttl) {
		currentRefreshJtiBySession.put(key(userId, sessionId), refreshJti);
	}

	@Override
	public synchronized RefreshTokenRotationResult rotate(
		Long userId,
		String sessionId,
		String expectedRefreshJti,
		String newRefreshJti,
		Duration ttl
	) {
		String key = key(userId, sessionId);
		if (isRevoked(userId, sessionId) || !expectedRefreshJti.equals(currentRefreshJtiBySession.get(key))) {
			return RefreshTokenRotationResult.REJECTED;
		}
		currentRefreshJtiBySession.put(key, newRefreshJti);
		return RefreshTokenRotationResult.ROTATED;
	}

	@Override
	public synchronized void deleteSession(Long userId, String sessionId) {
		currentRefreshJtiBySession.remove(key(userId, sessionId));
	}

	@Override
	public synchronized void deleteAllSessions(Long userId) {
		currentRefreshJtiBySession.keySet().removeIf(key -> key.startsWith(userId + ":"));
	}

	@Override
	public synchronized void revoke(Long userId, String sessionId, Duration ttl) {
		String key = key(userId, sessionId);
		currentRefreshJtiBySession.remove(key);
		revokedSessions.put(key, Boolean.TRUE);
	}

	@Override
	public boolean isRevoked(Long userId, String sessionId) {
		return Boolean.TRUE.equals(revokedSessions.get(key(userId, sessionId)));
	}

	public boolean contains(Long userId, String sessionId, String refreshJti) {
		return refreshJti.equals(currentRefreshJtiBySession.get(key(userId, sessionId)));
	}

	public boolean hasSession(Long userId, String sessionId) {
		return currentRefreshJtiBySession.containsKey(key(userId, sessionId));
	}

	private String key(Long userId, String sessionId) {
		return userId + ":" + sessionId;
	}
}
