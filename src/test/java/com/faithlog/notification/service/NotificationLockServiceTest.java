package com.faithlog.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.notification.service.port.NotificationLockPort;
import com.faithlog.notification.service.port.NotificationRedisOperationException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NotificationLockServiceTest {

	private final FakeNotificationLockPort port = new FakeNotificationLockPort();
	private final NotificationLockService service = new NotificationLockService(port);

	@Test
	void acquireScheduledLock_blocks_concurrent_job_and_uses_default_10_minute_ttl() {
		NotificationLockKey key = new NotificationLockKey("devotion-missing", 1L, "week:2026-06-15");

		Optional<NotificationLockLease> first = service.acquireScheduledLock(key);
		Optional<NotificationLockLease> second = service.acquireScheduledLock(key);

		assertThat(first).isPresent();
		assertThat(second).isEmpty();
		assertThat(port.lastKey().value()).isEqualTo("notification:lock:devotion-missing:1:week:2026-06-15");
		assertThat(port.lastTtl()).isEqualTo(Duration.ofMinutes(10));
	}

	@Test
	void acquireScheduledLock_allows_custom_ttl_for_long_batch_job() {
		NotificationLockKey key = new NotificationLockKey("long-batch", 1L, "week:2026-06-15");
		Duration ttl = Duration.ofMinutes(45);

		Optional<NotificationLockLease> lease = service.acquireScheduledLock(key, ttl);

		assertThat(lease).isPresent();
		assertThat(port.lastTtl()).isEqualTo(ttl);
	}

	@Test
	void acquireScheduledLock_fails_closed_when_redis_fails() {
		port.fail = true;

		Optional<NotificationLockLease> lease = service.acquireScheduledLock(
			new NotificationLockKey("devotion-missing", 1L, "week:2026-06-15")
		);

		assertThat(lease).isEmpty();
	}

	@Test
	void acquireManualLock_throws_api_error_when_redis_fails() {
		port.fail = true;

		assertThatThrownBy(() -> service.acquireManualLock(new NotificationLockKey(
			"manual-admin-notification",
			1L,
			"requester:10"
		))).isInstanceOfSatisfying(BusinessException.class, exception ->
			assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOTIFICATION_REDIS_UNAVAILABLE)
		);
	}

	private static class FakeNotificationLockPort implements NotificationLockPort {

		private final Set<String> lockedKeys = new HashSet<>();
		private NotificationLockKey lastKey;
		private Duration lastTtl;
		private boolean fail;

		@Override
		public Optional<NotificationLockLease> acquire(NotificationLockKey key, Duration ttl) {
			if (fail) {
				throw new NotificationRedisOperationException("Redis lock failed");
			}
			this.lastKey = key;
			this.lastTtl = ttl;
			if (!lockedKeys.add(key.value())) {
				return Optional.empty();
			}
			return Optional.of(new NotificationLockLease(key, "owner-token"));
		}

		@Override
		public void release(NotificationLockLease lease) {
			lockedKeys.remove(lease.key().value());
		}

		private NotificationLockKey lastKey() {
			return lastKey;
		}

		private Duration lastTtl() {
			return lastTtl;
		}
	}
}
