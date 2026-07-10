package com.faithlog.user.infrastructure.redis;

import com.faithlog.user.service.port.RefreshTokenStore;
import java.time.Duration;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RedisRefreshTokenStore implements RefreshTokenStore {

	private static final String KEY_PREFIX = "auth:refresh:";

	private final StringRedisTemplate redisTemplate;

	public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void saveCurrent(Long userId, String sessionId, String refreshJti, Duration ttl) {
		redisTemplate.opsForValue().set(key(userId, sessionId), refreshJti, ttl);
	}

	@Override
	public boolean matchesCurrent(Long userId, String sessionId, String refreshJti) {
		String currentRefreshJti = redisTemplate.opsForValue().get(key(userId, sessionId));
		return refreshJti.equals(currentRefreshJti);
	}

	@Override
	public void deleteSession(Long userId, String sessionId) {
		redisTemplate.delete(key(userId, sessionId));
	}

	@Override
	public void deleteAllSessions(Long userId) {
		Set<String> keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
		if (keys == null || keys.isEmpty()) {
			return;
		}
		redisTemplate.delete(keys);
	}

	private String key(Long userId, String sessionId) {
		return KEY_PREFIX + userId + ":" + sessionId;
	}
}
