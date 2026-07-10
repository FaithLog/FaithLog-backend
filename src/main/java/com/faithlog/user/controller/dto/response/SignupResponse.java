package com.faithlog.user.controller.dto.response;

import com.faithlog.user.service.result.SignupResult;

public record SignupResponse(
	Long id,
	String name,
	String email,
	String role,
	boolean isActive
) {

	public static SignupResponse from(SignupResult result) {
		return new SignupResponse(
			result.id(),
			result.name(),
			result.email(),
			result.role(),
			result.isActive()
		);
	}
}
