package com.faithlog.notification.application;

import java.util.UUID;

public record SendNotificationResult(
	UUID notificationRequestId,
	int queuedCount,
	int skippedCount
) {
}
