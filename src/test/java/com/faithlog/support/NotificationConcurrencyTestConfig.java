package com.faithlog.support;

import com.faithlog.notification.service.NotificationDeduplicationKey;
import com.faithlog.notification.service.NotificationLockKey;
import com.faithlog.notification.service.NotificationLockLease;
import com.faithlog.notification.service.port.NotificationDeduplicationPort;
import com.faithlog.notification.service.port.NotificationLockPort;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class NotificationConcurrencyTestConfig {

	@Bean
	InMemoryNotificationConcurrencyPort notificationConcurrencyPort() {
		return new InMemoryNotificationConcurrencyPort();
	}

	public static class InMemoryNotificationConcurrencyPort implements NotificationDeduplicationPort, NotificationLockPort {

		private final Set<String> dedupKeys = new HashSet<>();
		private final Set<String> lockKeys = new HashSet<>();
		private boolean fail;

		@Override
		public boolean reserve(NotificationDeduplicationKey key, Duration ttl) {
			if (fail) {
				throw new com.faithlog.notification.service.port.NotificationRedisOperationException("test failure");
			}
			return dedupKeys.add(key.value());
		}

		@Override
		public Optional<NotificationLockLease> acquire(NotificationLockKey key, Duration ttl) {
			if (fail) {
				throw new com.faithlog.notification.service.port.NotificationRedisOperationException("test failure");
			}
			if (!lockKeys.add(key.value())) {
				return Optional.empty();
			}
			return Optional.of(new NotificationLockLease(key, "test-owner"));
		}

		@Override
		public void release(NotificationLockLease lease) {
			lockKeys.remove(lease.key().value());
		}

		public void fail() {
			this.fail = true;
		}

		public void reset() {
			this.fail = false;
			this.dedupKeys.clear();
			this.lockKeys.clear();
		}
	}
}
