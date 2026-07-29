package com.faithlog.user.service.result;

public record EmailVerificationResult(
	String token,
	long expiresInSeconds
) {
}
