package com.faithlog.notification.service.query;

import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.domain.type.SendStatus;
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
