package com.faithlog.user.application.port;

public record CurrentDeviceFcmTokenDeactivationCommand(
	Long userId,
	String clientInstanceId,
	String fcmToken
) {
}
