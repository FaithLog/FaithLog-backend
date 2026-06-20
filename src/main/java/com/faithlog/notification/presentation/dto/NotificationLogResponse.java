package com.faithlog.notification.presentation.dto;

import com.faithlog.notification.application.NotificationLogItemResult;
import com.faithlog.notification.domain.NotificationType;
import com.faithlog.notification.domain.SendStatus;
import java.time.Instant;
import java.time.LocalDate;

public record NotificationLogResponse(
	Long notificationLogId,
	String requestId,
	Long userId,
	String name,
	String email,
	Long campusId,
	NotificationType notificationType,
	LocalDate targetWeekStartDate,
	Long targetId,
	String title,
	String body,
	SendStatus sendStatus,
	String failureReason,
	Instant sentAt,
	Instant createdAt
) {

	public static NotificationLogResponse from(NotificationLogItemResult result) {
		return new NotificationLogResponse(
			result.notificationLogId(),
			result.requestId().toString(),
			result.userId(),
			result.name(),
			result.email(),
			result.campusId(),
			result.notificationType(),
			result.targetWeekStartDate(),
			result.targetId(),
			result.title(),
			result.body(),
			result.sendStatus(),
			result.failureReason(),
			result.sentAt(),
			result.createdAt()
		);
	}
}
