package com.faithlog.notification.application;

import com.faithlog.notification.domain.NotificationLog;
import com.faithlog.notification.domain.NotificationType;
import com.faithlog.notification.domain.SendStatus;
import com.faithlog.user.domain.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NotificationLogItemResult(
	Long notificationLogId,
	UUID requestId,
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

	public static NotificationLogItemResult of(NotificationLog log, User user) {
		return new NotificationLogItemResult(
			log.id(),
			log.requestId(),
			log.userId(),
			user.name(),
			user.email(),
			log.campusId(),
			log.notificationType(),
			log.targetWeekStartDate(),
			log.targetId(),
			log.title(),
			log.body(),
			log.sendStatus(),
			log.failureReason(),
			log.sentAt(),
			log.createdAt()
		);
	}
}
