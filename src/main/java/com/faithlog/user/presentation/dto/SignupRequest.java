package com.faithlog.user.presentation.dto;

import com.faithlog.user.application.SignupCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignupRequest(
	@NotBlank
	String name,

	@NotBlank
	@Email
	String email,

	@NotBlank
	String password
) {

	public SignupCommand toCommand() {
		return new SignupCommand(name, email, password);
	}
}
