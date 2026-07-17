package com.faithlog.notification.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.notification.service.NotificationDeduplicationKey;
import com.faithlog.notification.service.NotificationLockKey;
import com.faithlog.notification.service.NotificationLockLease;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisNotificationConcurrencyAdapterTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Test
	void deduplication_reserves_key_with_ttl_using_set_if_absent() {
		RedisNotificationDeduplicationAdapter adapter = new RedisNotificationDeduplicationAdapter(redisTemplate);
		NotificationDeduplicationKey key = NotificationDeduplicationKey.of("notification:dedup:test");
		Duration ttl = Duration.ofHours(25);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(eq(key.value()), org.mockito.ArgumentMatchers.anyString(), eq(ttl)))
			.thenReturn(true);

		var reservation = adapter.reserve(key, ttl);

		assertThat(reservation).isPresent();
		verify(valueOperations).setIfAbsent(eq(key.value()), eq(reservation.orElseThrow().ownerToken()), eq(ttl));
	}

	@Test
	void lock_acquires_key_with_owner_token_and_ttl_using_set_if_absent() {
		RedisNotificationLockAdapter adapter = new RedisNotificationLockAdapter(redisTemplate);
		NotificationLockKey key = NotificationLockKey.of("notification:lock:test");
		Duration ttl = Duration.ofMinutes(10);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(eq(key.value()), org.mockito.ArgumentMatchers.anyString(), eq(ttl)))
			.thenReturn(true);

		Optional<NotificationLockLease> lease = adapter.acquire(key, ttl);

		assertThat(lease).isPresent();
		assertThat(lease.get().key()).isEqualTo(key);
		assertThat(lease.get().ownerToken()).isNotBlank();
	}
}
