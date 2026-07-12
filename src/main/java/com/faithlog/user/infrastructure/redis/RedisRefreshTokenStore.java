package com.faithlog.user.infrastructure.redis;

import com.faithlog.user.service.port.RefreshTokenRotationResult;
import com.faithlog.user.service.port.RefreshTokenStore;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RedisRefreshTokenStore implements RefreshTokenStore {

	private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
		if redis.call('exists', KEYS[2]) == 1 then
		  return 0
		end
		if redis.call('get', KEYS[1]) ~= ARGV[1] then
		  return 0
		end
		redis.call('psetex', KEYS[1], ARGV[3], ARGV[2])
		return 1
		""", Long.class);

	private final StringRedisTemplate redisTemplate;

	public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void saveCurrent(Long userId, String sessionId, String refreshJti, Duration ttl) {
		redisTemplate.opsForValue().set(AuthRedisKeys.refresh(userId, sessionId), refreshJti, ttl);
	}

	@Override
	public RefreshTokenRotationResult rotate(
		Long userId,
		String sessionId,
		String expectedRefreshJti,
		String newRefreshJti,
		Duration ttl
	) {
		Long result = redisTemplate.execute(
			ROTATE_SCRIPT,
			List.of(AuthRedisKeys.refresh(userId, sessionId), AuthRedisKeys.revokedSession(userId, sessionId)),
			expectedRefreshJti,
			newRefreshJti,
			String.valueOf(ttl.toMillis())
		);
		return Long.valueOf(1L).equals(result)
			? RefreshTokenRotationResult.ROTATED
			: RefreshTokenRotationResult.REJECTED;
	}

	@Override
	public void deleteSession(Long userId, String sessionId) {
		redisTemplate.delete(AuthRedisKeys.refresh(userId, sessionId));
	}

	@Override
	public void deleteAllSessions(Long userId) {
		Set<String> keys = redisTemplate.keys(AuthRedisKeys.allRefreshSessions(userId));
		if (keys == null || keys.isEmpty()) {
			return;
		}
		redisTemplate.delete(keys);
	}
}
