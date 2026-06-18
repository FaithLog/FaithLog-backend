package com.faithlog.campus.presentation.dto;

import com.faithlog.campus.application.CreateCampusCommand;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCampusRequest(
	@NotBlank
	@Size(max = 100)
	String name,

	@Size(max = 100)
	String region,

	String description
) {

	public CreateCampusCommand toCommand(AuthenticatedUser authenticatedUser) {
		return new CreateCampusCommand(
			authenticatedUser.userId(),
			authenticatedUser.role(),
			name,
			region,
			description
		);
	}
}
