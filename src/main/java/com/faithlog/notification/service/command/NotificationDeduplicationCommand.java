package com.faithlog.notification.service.command;

import com.faithlog.notification.domain.type.NotificationType;
import java.time.LocalDate;

public record NotificationDeduplicationCommand(
	NotificationType notificationType,
	Long campusId,
	String scopeId,
	Long targetUserId,
	LocalDate businessDate
) {
}
