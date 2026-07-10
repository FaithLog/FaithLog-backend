package com.faithlog.notification.service;

public interface NotificationRetryBackoff {

	void sleepBeforeRetry(int retryNumber);
}
