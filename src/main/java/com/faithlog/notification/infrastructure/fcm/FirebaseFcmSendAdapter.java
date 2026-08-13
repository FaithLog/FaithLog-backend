package com.faithlog.notification.infrastructure.fcm;

import com.faithlog.global.observability.ExternalService;
import com.faithlog.global.observability.OperationalEventPort;
import com.faithlog.notification.service.FcmSendException;
import com.faithlog.notification.service.port.FcmSendCommand;
import com.faithlog.notification.service.port.FcmSendPort;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

class FirebaseFcmSendAdapter implements FcmSendPort {

	private final FirebaseMessagingClient firebaseMessagingClient;
	private final OperationalEventPort operationalEvents;

	FirebaseFcmSendAdapter(
		FirebaseMessagingClient firebaseMessagingClient,
		OperationalEventPort operationalEvents
	) {
		this.firebaseMessagingClient = firebaseMessagingClient;
		this.operationalEvents = operationalEvents;
	}

	@Override
	public void send(FcmSendCommand command) {
		try {
			firebaseMessagingClient.send(message(command));
		} catch (FirebaseFcmFailure failure) {
			FcmSendException exception = FirebaseFcmFailureClassifier.toFcmSendException(failure);
			if (exception.failureType() == com.faithlog.notification.service.port.FcmSendFailureType.TRANSIENT) {
				operationalEvents.externalServiceFailure(ExternalService.FCM);
			}
			throw exception;
		}
	}

	private Message message(FcmSendCommand command) {
		Message.Builder builder = Message.builder()
			.setToken(command.token())
			.setNotification(Notification.builder()
				.setTitle(command.title())
				.setBody(command.body())
				.build());
		if (!command.data().isEmpty()) {
			builder.putAllData(command.data());
		}
		return builder.build();
	}
}
