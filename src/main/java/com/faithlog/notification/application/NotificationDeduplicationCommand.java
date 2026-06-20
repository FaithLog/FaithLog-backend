package com.faithlog.notification.application;

import com.faithlog.notification.domain.NotificationType;
import java.time.LocalDate;

public record NotificationDeduplicationCommand(
	NotificationType notificationType,
	Long campusId,
	String scopeId,
	Long targetUserId,
	LocalDate businessDate
) {
}
