package com.faithlog.notification.infrastructure.redis;

import com.faithlog.notification.service.NotificationDeduplicationKey;
import com.faithlog.notification.service.NotificationDeduplicationReservation;
import com.faithlog.notification.service.port.NotificationDeduplicationPort;
import com.faithlog.notification.service.port.NotificationRedisOperationException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RedisNotificationDeduplicationAdapter implements NotificationDeduplicationPort {

	private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
		"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
		Long.class
	);

	private final StringRedisTemplate redisTemplate;

	public RedisNotificationDeduplicationAdapter(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public Optional<NotificationDeduplicationReservation> reserve(NotificationDeduplicationKey key, Duration ttl) {
		String ownerToken = UUID.randomUUID().toString();
		try {
			if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key.value(), ownerToken, ttl))) {
				return Optional.empty();
			}
			return Optional.of(new NotificationDeduplicationReservation(key, ownerToken));
		} catch (RuntimeException exception) {
			throw new NotificationRedisOperationException("Notification deduplication Redis operation failed", exception);
		}
	}

	@Override
	public void release(NotificationDeduplicationReservation reservation) {
		try {
			redisTemplate.execute(
				RELEASE_SCRIPT,
				List.of(reservation.key().value()),
				reservation.ownerToken()
			);
		} catch (RuntimeException exception) {
			throw new NotificationRedisOperationException("Notification deduplication Redis release failed", exception);
		}
	}
}
