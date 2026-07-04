package com.faithlog.notification.presentation.dto;

import com.faithlog.notification.application.FcmTokenResult;
import com.faithlog.notification.domain.DeviceType;
import java.time.Instant;

public record FcmTokenResponse(
	Long tokenId,
	String token,
	DeviceType deviceType,
	String clientInstanceId,
	String appVersion,
	boolean isActive,
	Instant lastSeenAt,
	Instant lastRefreshedAt
) {

	public static FcmTokenResponse from(FcmTokenResult result) {
		return new FcmTokenResponse(
			result.id(),
			result.token(),
			result.deviceType(),
			result.clientInstanceId(),
			result.appVersion(),
			result.isActive(),
			result.lastSeenAt(),
			result.lastRefreshedAt()
		);
	}
}
