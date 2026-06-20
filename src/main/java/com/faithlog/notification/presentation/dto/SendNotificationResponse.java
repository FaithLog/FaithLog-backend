package com.faithlog.notification.presentation.dto;

import com.faithlog.notification.application.SendNotificationResult;

public record SendNotificationResponse(
	String notificationRequestId,
	int queuedCount,
	int skippedCount
) {

	public static SendNotificationResponse from(SendNotificationResult result) {
		return new SendNotificationResponse(
			result.notificationRequestId().toString(),
			result.queuedCount(),
			result.skippedCount()
		);
	}
}
