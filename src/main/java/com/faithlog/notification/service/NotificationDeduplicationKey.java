package com.faithlog.notification.service;

import com.faithlog.notification.service.command.NotificationDeduplicationCommand;
import java.util.Objects;

public record NotificationDeduplicationKey(String value) {

	public NotificationDeduplicationKey {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Notification deduplication key must not be blank");
		}
	}

	public static NotificationDeduplicationKey of(String value) {
		return new NotificationDeduplicationKey(value);
	}

	static NotificationDeduplicationKey automatic(NotificationDeduplicationCommand command) {
		Objects.requireNonNull(command.notificationType(), "notificationType must not be null");
		Objects.requireNonNull(command.campusId(), "campusId must not be null");
		Objects.requireNonNull(command.scopeId(), "scopeId must not be null");
		Objects.requireNonNull(command.targetUserId(), "targetUserId must not be null");
		Objects.requireNonNull(command.businessDate(), "businessDate must not be null");
		return new NotificationDeduplicationKey(String.join(
			":",
			"notification",
			"dedup",
			command.notificationType().name(),
			command.campusId().toString(),
			command.scopeId(),
			command.targetUserId().toString(),
			command.businessDate().toString()
		));
	}
}
