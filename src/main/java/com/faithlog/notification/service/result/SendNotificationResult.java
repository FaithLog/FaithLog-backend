package com.faithlog.notification.service.result;

import java.util.UUID;

public record SendNotificationResult(
	UUID notificationRequestId,
	int queuedCount,
	int skippedCount
) {
}
