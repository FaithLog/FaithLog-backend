package com.faithlog.notification.application;

import com.faithlog.notification.domain.DeviceType;

public record RegisterFcmTokenCommand(
	Long userId,
	String token,
	String clientInstanceId,
	DeviceType deviceType,
	String appVersion
) {
}
