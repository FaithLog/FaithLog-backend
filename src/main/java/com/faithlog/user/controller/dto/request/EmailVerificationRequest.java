package com.faithlog.user.controller.dto.request;

import com.faithlog.user.service.command.RequestEmailVerificationCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailVerificationRequest(
	@NotBlank
	@Email
	String email
) {

	public RequestEmailVerificationCommand toCommand() {
		return new RequestEmailVerificationCommand(email);
	}
}
