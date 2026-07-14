package com.faithlog.notification.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.notification.service.command.NotificationDeduplicationCommand;
import com.faithlog.notification.service.port.NotificationDeduplicationPort;
import com.faithlog.notification.service.port.NotificationRedisOperationException;
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

	public boolean reserveDailyRequiredNotification(NotificationDeduplicationCommand command) {
		try {
			return deduplicationPort.reserve(NotificationDeduplicationKey.automatic(command), DAILY_DEDUP_TTL);
		} catch (NotificationRedisOperationException exception) {
			throw new BusinessException(ErrorCode.NOTIFICATION_REDIS_UNAVAILABLE);
		}
	}

	public void releaseRequiredNotification(NotificationDeduplicationCommand command) {
		try {
			deduplicationPort.release(NotificationDeduplicationKey.automatic(command));
		} catch (NotificationRedisOperationException ignored) {
			// Preserve the original transactional failure while allowing the short TTL to expire safely.
		}
	}

	private boolean reserveAutomaticNotification(NotificationDeduplicationCommand command, Duration ttl) {
		try {
			return deduplicationPort.reserve(NotificationDeduplicationKey.automatic(command), ttl);
		} catch (NotificationRedisOperationException exception) {
			return false;
		}
	}
}
