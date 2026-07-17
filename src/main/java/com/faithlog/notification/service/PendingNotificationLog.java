package com.faithlog.notification.service;

import com.faithlog.notification.domain.entity.NotificationLog;

record PendingNotificationLog(
	Long id,
	Long userId,
	Long campusId,
	String title,
	String body
) {

	static PendingNotificationLog from(NotificationLog log) {
		return new PendingNotificationLog(
			log.id(),
			log.userId(),
			log.campusId(),
			log.title(),
			log.body()
		);
	}
}
