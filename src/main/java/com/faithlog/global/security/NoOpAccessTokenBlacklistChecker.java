package com.faithlog.global.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(AccessTokenBlacklistChecker.class)
public class NoOpAccessTokenBlacklistChecker implements AccessTokenBlacklistChecker {

	@Override
	public boolean isBlacklisted(String jti) {
		return false;
	}
}
