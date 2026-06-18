package com.faithlog.global.security;

import java.time.Instant;

public record AuthenticatedUser(
	Long userId,
	String role,
	String sessionId,
	String jti,
	Instant accessTokenExpiresAt
) {
}
