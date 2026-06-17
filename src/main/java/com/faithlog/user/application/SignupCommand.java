package com.faithlog.user.application;

public record SignupCommand(
	String name,
	String email,
	String password
) {
}
