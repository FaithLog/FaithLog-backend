package com.faithlog.user.support;

import com.faithlog.user.application.port.AccessTokenBlacklistStore;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAccessTokenBlacklistStore implements AccessTokenBlacklistStore {

	private final Set<String> blacklistedJtis = ConcurrentHashMap.newKeySet();

	@Override
	public void blacklist(String jti, Duration ttl) {
		blacklistedJtis.add(jti);
	}

	@Override
	public boolean isBlacklisted(String jti) {
		return blacklistedJtis.contains(jti);
	}
}
