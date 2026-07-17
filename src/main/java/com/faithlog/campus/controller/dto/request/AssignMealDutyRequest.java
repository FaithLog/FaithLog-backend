package com.faithlog.campus.controller.dto.request;

import com.faithlog.campus.service.command.AssignMealDutyCommand;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;

public record AssignMealDutyRequest(
	@NotNull Long userId
) {

	public AssignMealDutyCommand toCommand(Long campusId, AuthenticatedUser authenticatedUser) {
		return new AssignMealDutyCommand(campusId, authenticatedUser.userId(), userId);
	}
}
