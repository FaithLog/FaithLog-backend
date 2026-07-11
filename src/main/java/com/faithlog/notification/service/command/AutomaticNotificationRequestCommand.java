package com.faithlog.notification.service.command;

import com.faithlog.notification.domain.type.NotificationType;
import java.time.LocalDate;
import java.util.List;

public record AutomaticNotificationRequestCommand(
	Long campusId,
	NotificationType notificationType,
	LocalDate targetWeekStartDate,
	Long targetId,
	List<Long> targetUserIds,
	LocalDate businessDate,
	String scopeId,
	String title,
	String body
) {
}
