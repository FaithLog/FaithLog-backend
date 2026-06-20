package com.faithlog.notification.infrastructure.fcm;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.MessagingErrorCode;

class FirebaseFcmFailure extends Exception {

	private final ErrorCode errorCode;
	private final MessagingErrorCode messagingErrorCode;

	FirebaseFcmFailure(ErrorCode errorCode, MessagingErrorCode messagingErrorCode, String message) {
		super(message);
		this.errorCode = errorCode;
		this.messagingErrorCode = messagingErrorCode;
	}

	ErrorCode errorCode() {
		return errorCode;
	}

	MessagingErrorCode messagingErrorCode() {
		return messagingErrorCode;
	}
}
