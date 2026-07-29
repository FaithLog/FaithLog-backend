package com.faithlog.user.service.policy;

import java.time.Duration;

public record EmailVerificationPolicy(
	Duration challengeTtl,
	Duration resendCooldown,
	Duration rateLimitWindow,
	Duration grantTtl,
	int maxAttempts,
	int maxRequestsPerWindow
) {

	public EmailVerificationPolicy {
		if (challengeTtl.isNegative() || challengeTtl.isZero()
			|| resendCooldown.isNegative() || resendCooldown.isZero()
			|| rateLimitWindow.isNegative() || rateLimitWindow.isZero()
			|| grantTtl.isNegative() || grantTtl.isZero()
			|| maxAttempts <= 0 || maxRequestsPerWindow <= 0) {
			throw new IllegalArgumentException("Email verification policy values must be positive");
		}
	}
}
