package com.faithlog.notification.infrastructure.fcm;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.notification.application.FcmSendException;
import com.faithlog.notification.application.port.FcmSendCommand;
import com.faithlog.notification.application.port.FcmSendFailureType;
import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;

class FirebaseFcmSendAdapterTest {

	@Test
	void maps_unregistered_and_token_not_registered_to_permanent_failure() {
		FirebaseFcmSendAdapter adapter = adapterThrowing(FirebaseFcmFailureClassifier.failure(
			ErrorCode.NOT_FOUND,
			MessagingErrorCode.UNREGISTERED,
			"Requested entity was not found."
		));

		assertThatThrownBy(() -> adapter.send(command()))
			.isInstanceOf(FcmSendException.class)
			.extracting("failureType")
			.isEqualTo(FcmSendFailureType.PERMANENT);
	}

	@Test
	void maps_rate_limit_timeout_and_temporary_firebase_errors_to_transient_failure() {
		FirebaseFcmSendAdapter adapter = adapterThrowing(FirebaseFcmFailureClassifier.failure(
			ErrorCode.DEADLINE_EXCEEDED,
			MessagingErrorCode.QUOTA_EXCEEDED,
			"deadline exceeded"
		));

		assertThatThrownBy(() -> adapter.send(command()))
			.isInstanceOf(FcmSendException.class)
			.extracting("failureType")
			.isEqualTo(FcmSendFailureType.TRANSIENT);
	}

	@Test
	void maps_invalid_argument_to_permanent_only_when_payload_is_known_valid() {
		FirebaseFcmSendAdapter adapter = adapterThrowing(FirebaseFcmFailureClassifier.failure(
			ErrorCode.INVALID_ARGUMENT,
			MessagingErrorCode.INVALID_ARGUMENT,
			"The registration token is not a valid FCM registration token"
		));

		assertThatThrownBy(() -> adapter.send(command()))
			.isInstanceOf(FcmSendException.class)
			.extracting("failureType")
			.isEqualTo(FcmSendFailureType.PERMANENT);
	}

	private FirebaseFcmSendAdapter adapterThrowing(FirebaseFcmFailure failure) {
		return new FirebaseFcmSendAdapter(message -> {
			throw failure;
		});
	}

	private FcmSendCommand command() {
		return new FcmSendCommand("fcm-token", "제목", "본문");
	}
}
