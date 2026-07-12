package com.faithlog.user.infrastructure.redis;

import com.faithlog.global.security.SessionRevocationChecker;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RedisSessionRevocationChecker implements SessionRevocationChecker {

	private final StringRedisTemplate redisTemplate;

	public RedisSessionRevocationChecker(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public boolean isRevoked(Long userId, String sessionId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(AuthRedisKeys.revokedSession(userId, sessionId)));
	}
}
