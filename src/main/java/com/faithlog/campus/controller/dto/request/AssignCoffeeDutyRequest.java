package com.faithlog.campus.controller.dto.request;

import com.faithlog.campus.service.command.AssignCoffeeDutyCommand;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;

public record AssignCoffeeDutyRequest(
	@NotNull Long userId
) {

	public AssignCoffeeDutyCommand toCommand(Long campusId, AuthenticatedUser authenticatedUser) {
		return new AssignCoffeeDutyCommand(campusId, authenticatedUser.userId(), userId);
	}
}
