package com.faithlog.user.controller.dto.response;

import com.faithlog.user.service.result.EmailVerificationResult;

public record SignupEmailVerificationResponse(
	String emailVerificationToken,
	long expiresInSeconds
) {

	public static SignupEmailVerificationResponse from(EmailVerificationResult result) {
		return new SignupEmailVerificationResponse(result.token(), result.expiresInSeconds());
	}
}
