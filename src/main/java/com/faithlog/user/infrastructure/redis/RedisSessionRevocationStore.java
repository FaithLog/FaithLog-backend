package com.faithlog.user.infrastructure.redis;

import com.faithlog.user.service.port.SessionRevocationStore;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RedisSessionRevocationStore implements SessionRevocationStore {

	private static final String REVOKED_VALUE = "revoked";
	private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>("""
		redis.call('del', KEYS[1])
		redis.call('psetex', KEYS[2], ARGV[1], ARGV[2])
		return 1
		""", Long.class);

	private final StringRedisTemplate redisTemplate;

	public RedisSessionRevocationStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void revoke(Long userId, String sessionId, Duration ttl) {
		Long result = redisTemplate.execute(
			REVOKE_SCRIPT,
			List.of(AuthRedisKeys.refresh(userId, sessionId), AuthRedisKeys.revokedSession(userId, sessionId)),
			String.valueOf(ttl.toMillis()),
			REVOKED_VALUE
		);
		if (!Long.valueOf(1L).equals(result)) {
			throw new IllegalStateException("Redis session revocation did not complete");
		}
	}

	@Override
	public boolean isRevoked(Long userId, String sessionId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(AuthRedisKeys.revokedSession(userId, sessionId)));
	}
}
