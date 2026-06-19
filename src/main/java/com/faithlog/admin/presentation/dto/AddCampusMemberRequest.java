package com.faithlog.admin.presentation.dto;

import com.faithlog.admin.application.AddCampusMemberCommand;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;

public record AddCampusMemberRequest(
	@NotNull Long userId
) {

	public AddCampusMemberCommand toCommand(Long campusId, AuthenticatedUser authenticatedUser) {
		return new AddCampusMemberCommand(authenticatedUser.userId(), campusId, userId);
	}
}
