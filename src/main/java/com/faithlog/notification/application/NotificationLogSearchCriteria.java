package com.faithlog.notification.application;

import com.faithlog.notification.domain.NotificationType;
import com.faithlog.notification.domain.SendStatus;
import java.time.LocalDate;
import java.util.UUID;

public record NotificationLogSearchCriteria(
	Long campusId,
	NotificationType notificationType,
	SendStatus sendStatus,
	LocalDate targetWeekStartDate,
	Long targetId,
	UUID requestId,
	LocalDate startDate,
	LocalDate endDate
) {
}
