package com.faithlog.user.presentation.dto;

import com.faithlog.user.application.RefreshCommand;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
	@NotBlank
	String refreshToken
) {

	public RefreshCommand toCommand() {
		return new RefreshCommand(refreshToken);
	}
}
