package com.faithlog.notification.service;

import com.faithlog.notification.service.port.FcmSendFailureType;

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
