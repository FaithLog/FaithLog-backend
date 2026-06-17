package com.faithlog.user.presentation.dto;

import com.faithlog.user.domain.User;

public record SignupResponse(
	Long id,
	String name,
	String email,
	String role,
	boolean isActive
) {

	public static SignupResponse from(User user) {
		return new SignupResponse(
			user.id(),
			user.name(),
			user.email(),
			user.role().name(),
			user.isActive()
		);
	}
}
