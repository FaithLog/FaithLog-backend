package com.faithlog.user.controller.dto.request;

import com.faithlog.user.service.command.ChangeMyPasswordCommand;
import jakarta.validation.constraints.NotBlank;

public record ChangeMyPasswordRequest(
	@NotBlank
	String currentPassword,

	@NotBlank
	String newPassword
) {

	public ChangeMyPasswordCommand toCommand(Long userId) {
		return new ChangeMyPasswordCommand(userId, currentPassword, newPassword);
	}
}
