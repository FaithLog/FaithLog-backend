package com.faithlog.notification.application.port;

public class NotificationRedisOperationException extends RuntimeException {

	public NotificationRedisOperationException(String message) {
		super(message);
	}

	public NotificationRedisOperationException(String message, Throwable cause) {
		super(message, cause);
	}
}
