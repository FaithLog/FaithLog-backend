package com.faithlog.user.application;

import com.faithlog.user.domain.User;

public record SignupResult(
	Long id,
	String name,
	String email,
	String role,
	boolean isActive
) {

	public static SignupResult from(User user) {
		return new SignupResult(
			user.id(),
			user.name(),
			user.email(),
			user.role().name(),
			user.isActive()
		);
	}
}
