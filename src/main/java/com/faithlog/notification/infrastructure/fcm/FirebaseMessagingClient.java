package com.faithlog.notification.infrastructure.fcm;

import com.google.firebase.messaging.Message;

@FunctionalInterface
interface FirebaseMessagingClient {

	String send(Message message) throws FirebaseFcmFailure;
}
