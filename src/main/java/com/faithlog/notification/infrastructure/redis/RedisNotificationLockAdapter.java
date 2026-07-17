package com.faithlog.notification.infrastructure.redis;

import com.faithlog.notification.service.NotificationLockKey;
import com.faithlog.notification.service.NotificationLockLease;
import com.faithlog.notification.service.port.NotificationLockPort;
import com.faithlog.notification.service.port.NotificationRedisOperationException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RedisNotificationLockAdapter implements NotificationLockPort {

	private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
		"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
		Long.class
	);
	private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
		"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
		Long.class
	);

	private final StringRedisTemplate redisTemplate;

	public RedisNotificationLockAdapter(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public Optional<NotificationLockLease> acquire(NotificationLockKey key, Duration ttl) {
		String ownerToken = UUID.randomUUID().toString();
		try {
			boolean acquired = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key.value(), ownerToken, ttl));
			if (!acquired) {
				return Optional.empty();
			}
			return Optional.of(new NotificationLockLease(key, ownerToken));
		} catch (RuntimeException exception) {
			throw new NotificationRedisOperationException("Notification lock Redis operation failed", exception);
		}
	}

	@Override
	public boolean renew(NotificationLockLease lease, Duration ttl) {
		try {
			Long renewed = redisTemplate.execute(
				RENEW_SCRIPT,
				List.of(lease.key().value()),
				lease.ownerToken(),
				Long.toString(ttl.toMillis())
			);
			return renewed != null && renewed > 0;
		} catch (RuntimeException exception) {
			throw new NotificationRedisOperationException("Notification lock renewal Redis operation failed", exception);
		}
	}

	@Override
	public void release(NotificationLockLease lease) {
		try {
			redisTemplate.execute(RELEASE_SCRIPT, List.of(lease.key().value()), lease.ownerToken());
		} catch (RuntimeException exception) {
			throw new NotificationRedisOperationException("Notification lock release Redis operation failed", exception);
		}
	}
}
