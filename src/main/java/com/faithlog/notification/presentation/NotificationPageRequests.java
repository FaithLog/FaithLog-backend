package com.faithlog.notification.presentation;

import com.faithlog.global.exception.ErrorCode;
import com.faithlog.global.controller.PageSortRequestValidator;
import com.faithlog.global.controller.PageSortRequestValidator.SortValidationRule;
import java.util.List;
import org.springframework.data.domain.Pageable;

final class NotificationPageRequests {

	private static final SortValidationRule NOTIFICATION_LOG_SORT_RULE = new SortValidationRule(
		List.of("createdAt", "sentAt", "sendStatus", "notificationType", "targetWeekStartDate", "targetId"),
		ErrorCode.NOTIFICATION_INVALID_PAGE,
		ErrorCode.NOTIFICATION_INVALID_SIZE,
		ErrorCode.NOTIFICATION_INVALID_SORT_FORMAT,
		ErrorCode.NOTIFICATION_INVALID_SORT_PROPERTY,
		ErrorCode.NOTIFICATION_INVALID_SORT_DIRECTION
	);

	private NotificationPageRequests() {
	}

	static Pageable logs(int page, int size, String sort) {
		return PageSortRequestValidator.pageable(page, size, sort, NOTIFICATION_LOG_SORT_RULE);
	}
}
