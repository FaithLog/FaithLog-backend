package com.faithlog.user.presentation.dto;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.user.application.DeleteMyAccountCommand;
import jakarta.validation.constraints.NotBlank;

public record DeleteMyAccountRequest(
	@NotBlank
	String password,

	@NotBlank
	String confirmText
) {

	public DeleteMyAccountCommand toCommand(AuthenticatedUser authenticatedUser) {
		return new DeleteMyAccountCommand(
			authenticatedUser.userId(),
			authenticatedUser.sessionId(),
			authenticatedUser.jti(),
			authenticatedUser.accessTokenExpiresAt(),
			password,
			confirmText
		);
	}
}
