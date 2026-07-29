package com.faithlog.user.controller.dto.response;

import com.faithlog.user.service.result.EmailVerificationResult;

public record PasswordResetVerificationResponse(
	String passwordResetToken,
	long expiresInSeconds
) {

	public static PasswordResetVerificationResponse from(EmailVerificationResult result) {
		return new PasswordResetVerificationResponse(result.token(), result.expiresInSeconds());
	}
}
