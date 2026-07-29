package com.faithlog.user.controller.dto.request;

import com.faithlog.user.service.command.ConfirmEmailVerificationCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerificationConfirmRequest(
	@NotBlank
	@Email
	String email,

	@NotBlank
	@Pattern(regexp = "\\d{6}")
	String code
) {
	public EmailVerificationConfirmRequest {
		email = email == null ? null : email.trim();
	}

	public ConfirmEmailVerificationCommand toCommand() {
		return new ConfirmEmailVerificationCommand(email, code);
	}
}
