package com.faithlog.notification.infrastructure.fcm;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;

class FirebaseAdminMessagingClient implements FirebaseMessagingClient {

	private final FirebaseMessaging firebaseMessaging;

	FirebaseAdminMessagingClient(FirebaseMessaging firebaseMessaging) {
		this.firebaseMessaging = firebaseMessaging;
	}

	@Override
	public String send(Message message) throws FirebaseFcmFailure {
		try {
			return firebaseMessaging.send(message);
		} catch (FirebaseMessagingException exception) {
			throw FirebaseFcmFailureClassifier.failure(exception);
		}
	}
}
