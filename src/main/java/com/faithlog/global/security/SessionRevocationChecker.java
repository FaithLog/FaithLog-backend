package com.faithlog.global.security;

public interface SessionRevocationChecker {

	boolean isRevoked(Long userId, String sessionId);
}
