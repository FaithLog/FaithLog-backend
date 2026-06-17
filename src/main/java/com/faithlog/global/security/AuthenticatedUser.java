package com.faithlog.global.security;

public record AuthenticatedUser(
	Long userId,
	String role,
	String sessionId,
	String jti
) {
}
