package com.faithlog.notification.infrastructure.fcm;

import com.faithlog.notification.service.port.FcmSendCommand;
import com.faithlog.notification.service.port.FcmSendPort;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

class FirebaseFcmSendAdapter implements FcmSendPort {

	private final FirebaseMessagingClient firebaseMessagingClient;

	FirebaseFcmSendAdapter(FirebaseMessagingClient firebaseMessagingClient) {
		this.firebaseMessagingClient = firebaseMessagingClient;
	}

	@Override
	public void send(FcmSendCommand command) {
		try {
			firebaseMessagingClient.send(message(command));
		} catch (FirebaseFcmFailure failure) {
			throw FirebaseFcmFailureClassifier.toFcmSendException(failure);
		}
	}

	private Message message(FcmSendCommand command) {
		return Message.builder()
			.setToken(command.token())
			.setNotification(Notification.builder()
				.setTitle(command.title())
				.setBody(command.body())
				.build())
			.build();
	}
}
