package com.faithlog.notification.controller.dto.response;

import com.faithlog.notification.service.result.SendNotificationResult;

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
