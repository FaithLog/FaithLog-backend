package com.faithlog.notification.application;

import org.springframework.stereotype.Component;

@Component
public class ThreadSleepingNotificationRetryBackoff implements NotificationRetryBackoff {

	private static final long[] INTERVAL_MILLIS = {1000L, 5000L, 30000L};

	@Override
	public void sleepBeforeRetry(int retryNumber) {
		if (retryNumber < 1 || retryNumber > INTERVAL_MILLIS.length) {
			return;
		}
		try {
			Thread.sleep(INTERVAL_MILLIS[retryNumber - 1]);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}
}
