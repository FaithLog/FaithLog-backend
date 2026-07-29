package com.faithlog.user.controller.dto.response;

import com.faithlog.user.service.result.EmailVerificationRequestResult;

public record EmailVerificationRequestResponse(
	long expiresInSeconds,
	long resendAvailableInSeconds
) {

	public static EmailVerificationRequestResponse from(EmailVerificationRequestResult result) {
		return new EmailVerificationRequestResponse(
			result.expiresInSeconds(),
			result.resendAvailableInSeconds()
		);
	}
}
