package com.faithlog.notification.infrastructure.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.faithlog.global.observability.ExternalService;
import com.faithlog.global.observability.OperationalEventPort;
import com.faithlog.notification.service.FcmSendException;
import com.faithlog.notification.service.port.FcmSendCommand;
import com.faithlog.notification.service.port.FcmSendFailureType;
import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.MessagingErrorCode;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FirebaseFcmSendAdapterTest {
	private final OperationalEventPort operationalEvents = mock(OperationalEventPort.class);

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
		verify(operationalEvents, never()).externalServiceFailure(ExternalService.FCM);
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
		verify(operationalEvents).externalServiceFailure(ExternalService.FCM);
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

	@Test
	void sends_the_exact_data_payload_with_the_notification() {
		AtomicReference<com.google.firebase.messaging.Message> captured = new AtomicReference<>();
		FirebaseFcmSendAdapter adapter = new FirebaseFcmSendAdapter(message -> {
			captured.set(message);
			return "message-id";
		}, operationalEvents);
		Map<String, String> data = Map.of(
			"eventType", "ANNOUNCEMENT_PUBLISHED",
			"campusId", "7",
			"announcementId", "99",
			"categoryId", "3"
		);

		adapter.send(new FcmSendCommand("fcm-token", "새 공지", "[일반] 예배 안내", data));

		assertThat(ReflectionTestUtils.getField(captured.get(), "data")).isEqualTo(data);
	}

	private FirebaseFcmSendAdapter adapterThrowing(FirebaseFcmFailure failure) {
		return new FirebaseFcmSendAdapter(message -> {
			throw failure;
		}, operationalEvents);
	}

	private FcmSendCommand command() {
		return new FcmSendCommand("fcm-token", "제목", "본문");
	}
}
