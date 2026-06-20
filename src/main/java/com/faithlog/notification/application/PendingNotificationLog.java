package com.faithlog.notification.application;

import com.faithlog.notification.domain.NotificationLog;

record PendingNotificationLog(
	Long id,
	Long userId,
	String title,
	String body
) {

	static PendingNotificationLog from(NotificationLog log) {
		return new PendingNotificationLog(
			log.id(),
			log.userId(),
			log.title(),
			log.body()
		);
	}
}
