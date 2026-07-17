package com.faithlog.notification.infrastructure.fcm;

import com.faithlog.notification.service.FcmSendException;
import com.faithlog.notification.service.port.FcmSendFailureType;
import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import java.util.Locale;

final class FirebaseFcmFailureClassifier {

	private FirebaseFcmFailureClassifier() {
	}

	static FirebaseFcmFailure failure(ErrorCode errorCode, MessagingErrorCode messagingErrorCode, String message) {
		return new FirebaseFcmFailure(errorCode, messagingErrorCode, message);
	}

	static FirebaseFcmFailure failure(FirebaseMessagingException exception) {
		return failure(exception.getErrorCode(), exception.getMessagingErrorCode(), exception.getMessage());
	}

	static FcmSendException toFcmSendException(FirebaseFcmFailure failure) {
		FcmSendFailureType failureType = isPermanentFailure(failure)
			? FcmSendFailureType.PERMANENT
			: FcmSendFailureType.TRANSIENT;
		return new FcmSendException(failureType, failure.getMessage());
	}

	private static boolean isPermanentFailure(FirebaseFcmFailure failure) {
		if (failure.messagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
			return true;
		}
		String message = normalize(failure.getMessage());
		if (message.contains("token-not-registered") || message.contains("registration token is not registered")) {
			return true;
		}
		return failure.messagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT
			&& failure.errorCode() == ErrorCode.INVALID_ARGUMENT
			&& isPayloadValidInvalidTokenMessage(message);
	}

	private static boolean isPayloadValidInvalidTokenMessage(String message) {
		return message.contains("registration token")
			|| message.contains("invalid token")
			|| message.contains("invalid registration");
	}

	private static String normalize(String message) {
		if (message == null) {
			return "";
		}
		return message.toLowerCase(Locale.ROOT);
	}
}
