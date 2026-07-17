package com.faithlog.user.infrastructure.redis;

import com.faithlog.user.service.port.AccessTokenBlacklistStore;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RedisAccessTokenBlacklistStore implements AccessTokenBlacklistStore {

	private static final String KEY_PREFIX = "auth:access:blacklist:";
	private static final String BLACKLIST_VALUE = "revoked";

	private final StringRedisTemplate redisTemplate;

	public RedisAccessTokenBlacklistStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void blacklist(String jti, Duration ttl) {
		redisTemplate.opsForValue().set(key(jti), BLACKLIST_VALUE, ttl);
	}

	@Override
	public boolean isBlacklisted(String jti) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(key(jti)));
	}

	private String key(String jti) {
		return KEY_PREFIX + jti;
	}
}
