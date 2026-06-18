package com.faithlog.campus.presentation.dto;

import com.faithlog.campus.application.AssignCoffeeDutyCommand;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;

public record AssignCoffeeDutyRequest(
	@NotNull Long userId
) {

	public AssignCoffeeDutyCommand toCommand(Long campusId, AuthenticatedUser authenticatedUser) {
		return new AssignCoffeeDutyCommand(campusId, authenticatedUser.userId(), userId);
	}
}
