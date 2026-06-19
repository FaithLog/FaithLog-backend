package com.faithlog.campus.presentation.dto;

import com.faithlog.campus.application.UpdateCampusCommand;
import com.faithlog.global.security.AuthenticatedUser;

public record UpdateCampusRequest(
	String name,
	String region,
	String description,
	Boolean isActive
) {

	public UpdateCampusCommand toCommand(Long campusId, AuthenticatedUser authenticatedUser) {
		return new UpdateCampusCommand(
			authenticatedUser.userId(),
			campusId,
			name,
			region,
			description,
			isActive
		);
	}
}
