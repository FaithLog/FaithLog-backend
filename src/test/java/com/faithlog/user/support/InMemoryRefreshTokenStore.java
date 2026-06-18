package com.faithlog.user.support;

import com.faithlog.user.application.port.RefreshTokenStore;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRefreshTokenStore implements RefreshTokenStore {

	private final Map<String, String> currentRefreshJtiBySession = new ConcurrentHashMap<>();

	@Override
	public void saveCurrent(Long userId, String sessionId, String refreshJti, Duration ttl) {
		currentRefreshJtiBySession.put(key(userId, sessionId), refreshJti);
	}

	@Override
	public boolean matchesCurrent(Long userId, String sessionId, String refreshJti) {
		return refreshJti.equals(currentRefreshJtiBySession.get(key(userId, sessionId)));
	}

	@Override
	public void deleteSession(Long userId, String sessionId) {
		currentRefreshJtiBySession.remove(key(userId, sessionId));
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
