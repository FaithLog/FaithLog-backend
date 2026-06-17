package com.faithlog.user.presentation.dto;

import com.faithlog.user.application.LoginCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
	@NotBlank
	@Email
	String email,

	@NotBlank
	String password
) {

	public LoginCommand toCommand() {
		return new LoginCommand(email, password);
	}
}
