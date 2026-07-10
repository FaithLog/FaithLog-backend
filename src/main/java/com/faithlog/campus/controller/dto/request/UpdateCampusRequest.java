package com.faithlog.campus.controller.dto.request;

import com.faithlog.campus.service.command.UpdateCampusCommand;
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
