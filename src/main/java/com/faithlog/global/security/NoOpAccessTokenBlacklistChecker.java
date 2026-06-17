package com.faithlog.global.security;

import org.springframework.stereotype.Component;

@Component
public class NoOpAccessTokenBlacklistChecker implements AccessTokenBlacklistChecker {

	@Override
	public boolean isBlacklisted(String jti) {
		return false;
	}
}
