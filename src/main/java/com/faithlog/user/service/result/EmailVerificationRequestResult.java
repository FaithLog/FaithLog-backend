package com.faithlog.user.service.result;

public record EmailVerificationRequestResult(
	long expiresInSeconds,
	long resendAvailableInSeconds
) {
}
