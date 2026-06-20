package com.faithlog.notification.application;

import com.faithlog.notification.application.port.FcmSendFailureType;

public class FcmSendException extends RuntimeException {

	private final FcmSendFailureType failureType;

	public FcmSendException(FcmSendFailureType failureType, String message) {
		super(message);
		this.failureType = failureType;
	}

	public FcmSendFailureType failureType() {
		return failureType;
	}
}
