package com.faithlog.notification.infrastructure.redis;

import com.faithlog.notification.service.NotificationDeduplicationKey;
import com.faithlog.notification.service.port.NotificationDeduplicationPort;
import com.faithlog.notification.service.port.NotificationRedisOperationException;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RedisNotificationDeduplicationAdapter implements NotificationDeduplicationPort {

	private static final String RESERVED_VALUE = "reserved";

	private final StringRedisTemplate redisTemplate;

	public RedisNotificationDeduplicationAdapter(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public boolean reserve(NotificationDeduplicationKey key, Duration ttl) {
		try {
			return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key.value(), RESERVED_VALUE, ttl));
		} catch (RuntimeException exception) {
			throw new NotificationRedisOperationException("Notification deduplication Redis operation failed", exception);
		}
	}

	@Override
	public void release(NotificationDeduplicationKey key) {
		try {
			redisTemplate.delete(key.value());
		} catch (RuntimeException exception) {
			throw new NotificationRedisOperationException("Notification deduplication Redis release failed", exception);
		}
	}
}
