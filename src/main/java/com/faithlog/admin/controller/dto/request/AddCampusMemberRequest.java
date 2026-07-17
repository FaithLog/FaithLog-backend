package com.faithlog.admin.controller.dto.request;

import com.faithlog.admin.service.command.AddCampusMemberCommand;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;

public record AddCampusMemberRequest(
	@NotNull Long userId
) {

	public AddCampusMemberCommand toCommand(Long campusId, AuthenticatedUser authenticatedUser) {
		return new AddCampusMemberCommand(authenticatedUser.userId(), campusId, userId);
	}
}
