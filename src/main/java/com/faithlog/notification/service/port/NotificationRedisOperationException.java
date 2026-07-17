package com.faithlog.notification.service.port;

public class NotificationRedisOperationException extends RuntimeException {

	public NotificationRedisOperationException(String message) {
		super(message);
	}

	public NotificationRedisOperationException(String message, Throwable cause) {
		super(message, cause);
	}
}
