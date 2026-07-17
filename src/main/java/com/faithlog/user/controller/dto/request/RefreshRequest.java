package com.faithlog.user.controller.dto.request;

import com.faithlog.user.service.command.RefreshCommand;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
	@NotBlank
	String refreshToken
) {

	public RefreshCommand toCommand() {
		return new RefreshCommand(refreshToken);
	}
}
