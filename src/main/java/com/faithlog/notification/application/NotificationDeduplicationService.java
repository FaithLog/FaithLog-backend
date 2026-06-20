package com.faithlog.notification.application;

import com.faithlog.notification.application.port.NotificationDeduplicationPort;
import com.faithlog.notification.application.port.NotificationRedisOperationException;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class NotificationDeduplicationService {

	private static final Duration DAILY_DEDUP_TTL = Duration.ofHours(25);
	private static final Duration WEEKLY_DEDUP_TTL = Duration.ofDays(8);

	private final NotificationDeduplicationPort deduplicationPort;

	public NotificationDeduplicationService(NotificationDeduplicationPort deduplicationPort) {
		this.deduplicationPort = deduplicationPort;
	}

	public boolean reserveDailyAutomaticNotification(NotificationDeduplicationCommand command) {
		return reserveAutomaticNotification(command, DAILY_DEDUP_TTL);
	}

	public boolean reserveWeeklyAutomaticNotification(NotificationDeduplicationCommand command) {
		return reserveAutomaticNotification(command, WEEKLY_DEDUP_TTL);
	}

	private boolean reserveAutomaticNotification(NotificationDeduplicationCommand command, Duration ttl) {
		try {
			return deduplicationPort.reserve(NotificationDeduplicationKey.automatic(command), ttl);
		} catch (NotificationRedisOperationException exception) {
			return false;
		}
	}
}
