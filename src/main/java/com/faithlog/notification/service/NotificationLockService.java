package com.faithlog.notification.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.notification.service.port.NotificationLockPort;
import com.faithlog.notification.service.port.NotificationRedisOperationException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class NotificationLockService {

	private static final Duration DEFAULT_LOCK_TTL = Duration.ofMinutes(10);

	private final NotificationLockPort lockPort;

	public NotificationLockService(NotificationLockPort lockPort) {
		this.lockPort = lockPort;
	}

	public Optional<NotificationLockLease> acquireScheduledLock(NotificationLockKey key) {
		return acquireScheduledLock(key, DEFAULT_LOCK_TTL);
	}

	public Optional<NotificationLockLease> acquireScheduledLock(NotificationLockKey key, Duration ttl) {
		try {
			return lockPort.acquire(key, ttl);
		} catch (NotificationRedisOperationException exception) {
			return Optional.empty();
		}
	}

	public NotificationLockLease acquireManualLock(NotificationLockKey key) {
		try {
			return lockPort.acquire(key, DEFAULT_LOCK_TTL)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_LOCK_ALREADY_RUNNING));
		} catch (NotificationRedisOperationException exception) {
			throw new BusinessException(ErrorCode.NOTIFICATION_REDIS_UNAVAILABLE);
		}
	}

	public void release(NotificationLockLease lease) {
		if (lease == null) {
			return;
		}
		lockPort.release(lease);
	}
}
