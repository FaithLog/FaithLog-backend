package com.faithlog.user.application;

import java.time.Instant;

public record DeleteMyAccountCommand(
	Long userId,
	String sessionId,
	String accessJti,
	Instant accessTokenExpiresAt,
	String password,
	String confirmText
) {
}
