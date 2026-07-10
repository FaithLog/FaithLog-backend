package com.faithlog.user.service.port;

public record CurrentDeviceFcmTokenDeactivationCommand(
	Long userId,
	String clientInstanceId,
	String fcmToken
) {
}
