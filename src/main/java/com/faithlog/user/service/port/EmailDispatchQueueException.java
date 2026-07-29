package com.faithlog.user.service.port;

public class EmailDispatchQueueException extends RuntimeException {

	public EmailDispatchQueueException(String message) {
		super(message);
	}

	public EmailDispatchQueueException(String message, Throwable cause) {
		super(message, cause);
	}
}
