package com.faithlog.notification.service;

import java.util.Objects;

public record NotificationLockKey(String value) {

	public NotificationLockKey {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Notification lock key must not be blank");
		}
	}

	public NotificationLockKey(String jobName, Long campusId, String scopeId) {
		this(lockValue(jobName, campusId, scopeId));
	}

	public static NotificationLockKey of(String value) {
		return new NotificationLockKey(value);
	}

	public static NotificationLockKey manualAdminNotification(Long campusId, Long requesterId) {
		return new NotificationLockKey("manual-admin-notification", campusId, "requester:" + requesterId);
	}

	public static NotificationLockKey dispatch(Long campusId, java.util.UUID requestId) {
		return new NotificationLockKey("dispatch", campusId, requestId.toString());
	}

	private static String lockValue(String jobName, Long campusId, String scopeId) {
		Objects.requireNonNull(jobName, "jobName must not be null");
		Objects.requireNonNull(campusId, "campusId must not be null");
		Objects.requireNonNull(scopeId, "scopeId must not be null");
		return String.join(":", "notification", "lock", jobName, campusId.toString(), scopeId);
	}
}
