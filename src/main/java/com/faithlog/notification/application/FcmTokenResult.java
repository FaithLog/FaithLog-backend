package com.faithlog.notification.application;

import com.faithlog.notification.domain.DeviceType;
import com.faithlog.notification.domain.UserFcmToken;
import java.time.Instant;

public record FcmTokenResult(
	Long id,
	String token,
	DeviceType deviceType,
	String clientInstanceId,
	String appVersion,
	boolean isActive,
	Instant lastSeenAt,
	Instant lastRefreshedAt
) {

	public static FcmTokenResult from(UserFcmToken token) {
		return new FcmTokenResult(
			token.id(),
			token.token(),
			token.deviceType(),
			token.clientInstanceId(),
			token.appVersion(),
			token.isActive(),
			token.lastSeenAt(),
			token.lastRefreshedAt()
		);
	}
}
