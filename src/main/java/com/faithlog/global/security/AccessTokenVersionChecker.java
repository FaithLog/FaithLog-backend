package com.faithlog.global.security;

public interface AccessTokenVersionChecker {

	boolean matchesCurrentVersion(Long userId, long tokenVersion);
}
