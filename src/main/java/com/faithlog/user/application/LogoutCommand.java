package com.faithlog.user.application;

import java.time.Instant;

public record LogoutCommand(
	Long userId,
	String sessionId,
	String accessJti,
	Instant accessTokenExpiresAt,
	String refreshToken,
	String clientInstanceId,
	String fcmToken
) {
}
