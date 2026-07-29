package com.faithlog.user.service.port;

public class EmailVerificationStoreException extends RuntimeException {

	public EmailVerificationStoreException(String message) {
		super(message);
	}

	public EmailVerificationStoreException(String message, Throwable cause) {
		super(message, cause);
	}
}
