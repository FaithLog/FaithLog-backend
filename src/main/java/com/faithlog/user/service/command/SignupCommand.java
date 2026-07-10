package com.faithlog.user.service.command;

public record SignupCommand(
	String name,
	String email,
	String password
) {
}
