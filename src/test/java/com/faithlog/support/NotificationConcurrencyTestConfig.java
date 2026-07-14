package com.faithlog.support;

import com.faithlog.notification.service.NotificationDeduplicationKey;
import com.faithlog.notification.service.NotificationDeduplicationReservation;
import com.faithlog.notification.service.NotificationLockKey;
import com.faithlog.notification.service.NotificationLockLease;
import com.faithlog.notification.service.port.NotificationDeduplicationPort;
import com.faithlog.notification.service.port.NotificationLockPort;
import java.time.Duration;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

		private final Map<String, String> dedupKeys = new HashMap<>();
		private final Map<String, String> lockKeys = new HashMap<>();
		private boolean fail;
		private boolean failLockRelease;
		private boolean pauseDedupRelease;
		private boolean pauseLockAcquire;
		private CountDownLatch lockAcquireStarted = new CountDownLatch(1);
		private CountDownLatch allowLockAcquire = new CountDownLatch(1);
		private CountDownLatch dedupReleaseStarted = new CountDownLatch(1);
		private CountDownLatch allowDedupRelease = new CountDownLatch(1);

		@Override
		public Optional<NotificationDeduplicationReservation> reserve(NotificationDeduplicationKey key, Duration ttl) {
			if (fail) {
				throw new com.faithlog.notification.service.port.NotificationRedisOperationException("test failure");
			}
			String ownerToken = UUID.randomUUID().toString();
			if (dedupKeys.putIfAbsent(key.value(), ownerToken) != null) {
				return Optional.empty();
			}
			return Optional.of(new NotificationDeduplicationReservation(key, ownerToken));
		}

		@Override
		public void release(NotificationDeduplicationReservation reservation) {
			if (pauseDedupRelease) {
				dedupReleaseStarted.countDown();
				try {
					if (!allowDedupRelease.await(5, TimeUnit.SECONDS)) {
						throw new IllegalStateException("dedupe release timeout");
					}
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
				pauseDedupRelease = false;
			}
			dedupKeys.remove(reservation.key().value(), reservation.ownerToken());
		}

		@Override
		public Optional<NotificationLockLease> acquire(NotificationLockKey key, Duration ttl) {
			if (fail) {
				throw new com.faithlog.notification.service.port.NotificationRedisOperationException("test failure");
			}
			if (pauseLockAcquire) {
				pauseLockAcquire = false;
				lockAcquireStarted.countDown();
				try {
					if (!allowLockAcquire.await(5, TimeUnit.SECONDS)) {
						throw new IllegalStateException("lock acquire timeout");
					}
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
			}
			String ownerToken = UUID.randomUUID().toString();
			if (lockKeys.putIfAbsent(key.value(), ownerToken) != null) {
				return Optional.empty();
			}
			return Optional.of(new NotificationLockLease(key, ownerToken));
		}

		@Override
		public boolean renew(NotificationLockLease lease, Duration ttl) {
			if (fail) {
				throw new com.faithlog.notification.service.port.NotificationRedisOperationException("test failure");
			}
			return lease.ownerToken().equals(lockKeys.get(lease.key().value()));
		}

		@Override
		public void release(NotificationLockLease lease) {
			lockKeys.remove(lease.key().value(), lease.ownerToken());
			if (failLockRelease) {
				throw new com.faithlog.notification.service.port.NotificationRedisOperationException(
					"test lock release failure"
				);
			}
		}

		public void fail() {
			this.fail = true;
		}

		public void failLockRelease() {
			this.failLockRelease = true;
		}

		public void allowLockRelease() {
			this.failLockRelease = false;
		}

		public void pauseNextDedupRelease() {
			this.pauseDedupRelease = true;
			this.dedupReleaseStarted = new CountDownLatch(1);
			this.allowDedupRelease = new CountDownLatch(1);
		}

		public void pauseNextLockAcquire() {
			this.pauseLockAcquire = true;
			this.lockAcquireStarted = new CountDownLatch(1);
			this.allowLockAcquire = new CountDownLatch(1);
		}

		public boolean awaitLockAcquireStarted() throws InterruptedException {
			return lockAcquireStarted.await(5, TimeUnit.SECONDS);
		}

		public void allowLockAcquire() {
			allowLockAcquire.countDown();
		}

		public boolean awaitDedupReleaseStarted() throws InterruptedException {
			return dedupReleaseStarted.await(5, TimeUnit.SECONDS);
		}

		public void allowDedupRelease() {
			allowDedupRelease.countDown();
		}

		public void reset() {
			this.fail = false;
			this.failLockRelease = false;
			this.pauseDedupRelease = false;
			this.pauseLockAcquire = false;
			this.allowDedupRelease.countDown();
			this.allowLockAcquire.countDown();
			this.dedupReleaseStarted = new CountDownLatch(1);
			this.allowDedupRelease = new CountDownLatch(1);
			this.lockAcquireStarted = new CountDownLatch(1);
			this.allowLockAcquire = new CountDownLatch(1);
			this.dedupKeys.clear();
			this.lockKeys.clear();
		}
	}
}
