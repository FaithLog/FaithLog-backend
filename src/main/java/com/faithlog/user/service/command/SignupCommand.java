package com.faithlog.user.service.command;

public record SignupCommand(
	String name,
	String email,
	String password,
	String emailVerificationToken
) {

	public SignupCommand(String name, String email, String password) {
		this(name, email, password, null);
	}
}
