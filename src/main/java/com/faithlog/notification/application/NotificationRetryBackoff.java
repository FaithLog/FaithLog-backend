package com.faithlog.notification.application;

public interface NotificationRetryBackoff {

	void sleepBeforeRetry(int retryNumber);
}
