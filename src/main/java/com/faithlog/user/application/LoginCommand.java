package com.faithlog.user.application;

public record LoginCommand(
	String email,
	String password
) {
}
