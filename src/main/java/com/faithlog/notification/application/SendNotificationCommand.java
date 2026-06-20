package com.faithlog.notification.application;

import com.faithlog.notification.domain.NotificationType;
import java.time.LocalDate;
import java.util.List;

public record SendNotificationCommand(
	Long campusId,
	Long requesterId,
	NotificationType notificationType,
	List<Long> targetUserIds,
	LocalDate targetWeekStartDate,
	Long targetId,
	String title,
	String body
) {
}
