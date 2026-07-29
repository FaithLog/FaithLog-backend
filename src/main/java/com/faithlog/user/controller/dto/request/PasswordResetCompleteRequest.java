package com.faithlog.user.controller.dto.request;

import com.faithlog.user.service.command.CompletePasswordResetCommand;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetCompleteRequest(
	@NotBlank
	String resetToken,

	@NotBlank
	String newPassword
) {

	public CompletePasswordResetCommand toCommand() {
		return new CompletePasswordResetCommand(resetToken, newPassword);
	}
}
