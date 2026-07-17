package com.faithlog.notification.controller.dto.response;

import com.faithlog.notification.service.result.FcmTokenResult;
import com.faithlog.notification.domain.type.DeviceType;
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
