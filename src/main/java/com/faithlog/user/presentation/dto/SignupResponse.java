package com.faithlog.user.presentation.dto;

import com.faithlog.user.application.SignupResult;

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
