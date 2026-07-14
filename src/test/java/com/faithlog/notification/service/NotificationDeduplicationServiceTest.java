package com.faithlog.notification.service;

import com.faithlog.notification.service.command.NotificationDeduplicationCommand;
import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.notification.service.port.NotificationDeduplicationPort;
import com.faithlog.notification.service.port.NotificationRedisOperationException;
import com.faithlog.notification.domain.type.NotificationType;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NotificationDeduplicationServiceTest {

	private final FakeNotificationDeduplicationPort port = new FakeNotificationDeduplicationPort();
	private final NotificationDeduplicationService service = new NotificationDeduplicationService(port);

	@Test
	void reserveDailyAutomaticNotification_uses_business_key_and_25_hour_ttl() {
		NotificationDeduplicationCommand command = new NotificationDeduplicationCommand(
			NotificationType.DEVOTION_MISSING,
			1L,
			"week:2026-06-15",
			10L,
			LocalDate.of(2026, 6, 20)
		);

		assertThat(service.reserveDailyAutomaticNotification(command)).isTrue();
		assertThat(service.reserveDailyAutomaticNotification(command)).isFalse();

		assertThat(port.lastKey().value())
			.isEqualTo("notification:dedup:DEVOTION_MISSING:1:week:2026-06-15:10:2026-06-20");
		assertThat(port.lastTtl()).isEqualTo(Duration.ofHours(25));
	}

	@Test
	void reserveWeeklyAutomaticNotification_uses_8_day_ttl() {
		NotificationDeduplicationCommand command = new NotificationDeduplicationCommand(
			NotificationType.DEVOTION_MISSING,
			1L,
			"week:2026-06-15",
			10L,
			LocalDate.of(2026, 6, 15)
		);

		assertThat(service.reserveWeeklyAutomaticNotification(command)).isTrue();

		assertThat(port.lastTtl()).isEqualTo(Duration.ofDays(8));
	}

	@Test
	void reserveAutomaticNotification_fails_closed_when_redis_fails() {
		port.fail = true;

		boolean reserved = service.reserveDailyAutomaticNotification(new NotificationDeduplicationCommand(
			NotificationType.PAYMENT_UNPAID,
			1L,
			"payment:unpaid",
			10L,
			LocalDate.of(2026, 6, 20)
		));

		assertThat(reserved).isFalse();
	}

	private static class FakeNotificationDeduplicationPort implements NotificationDeduplicationPort {

		private final Set<String> reservedKeys = new HashSet<>();
		private NotificationDeduplicationKey lastKey;
		private Duration lastTtl;
		private boolean fail;

		@Override
		public boolean reserve(NotificationDeduplicationKey key, Duration ttl) {
			if (fail) {
				throw new NotificationRedisOperationException("Redis dedup failed");
			}
			this.lastKey = key;
			this.lastTtl = ttl;
			return reservedKeys.add(key.value());
		}

		@Override
		public void release(NotificationDeduplicationKey key) {
			reservedKeys.remove(key.value());
		}

		private NotificationDeduplicationKey lastKey() {
			return lastKey;
		}

		private Duration lastTtl() {
			return lastTtl;
		}
	}
}
