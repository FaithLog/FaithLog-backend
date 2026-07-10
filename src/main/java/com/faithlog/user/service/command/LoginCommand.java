package com.faithlog.user.service.command;

public record LoginCommand(
	String email,
	String password
) {
}
