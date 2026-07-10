package com.faithlog.notification.service;

import com.faithlog.notification.domain.entity.UserFcmToken;

record PendingFcmToken(
	Long id,
	String token
) {

	static PendingFcmToken from(UserFcmToken token) {
		return new PendingFcmToken(token.id(), token.token());
	}
}
