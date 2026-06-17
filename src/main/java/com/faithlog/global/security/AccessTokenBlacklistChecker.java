package com.faithlog.global.security;

public interface AccessTokenBlacklistChecker {

	boolean isBlacklisted(String jti);
}
