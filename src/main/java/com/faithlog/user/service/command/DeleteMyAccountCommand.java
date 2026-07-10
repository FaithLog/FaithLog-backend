package com.faithlog.user.service.command;

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
