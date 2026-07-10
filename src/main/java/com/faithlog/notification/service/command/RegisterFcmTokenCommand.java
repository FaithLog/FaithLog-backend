package com.faithlog.notification.service.command;

import com.faithlog.notification.domain.type.DeviceType;

public record RegisterFcmTokenCommand(
	Long userId,
	String token,
	String clientInstanceId,
	DeviceType deviceType,
	String appVersion
) {
}
