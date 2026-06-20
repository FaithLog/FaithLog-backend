package com.faithlog.notification.application;

import com.faithlog.notification.domain.UserFcmToken;

record PendingFcmToken(
	Long id,
	String token
) {

	static PendingFcmToken from(UserFcmToken token) {
		return new PendingFcmToken(token.id(), token.token());
	}
}
