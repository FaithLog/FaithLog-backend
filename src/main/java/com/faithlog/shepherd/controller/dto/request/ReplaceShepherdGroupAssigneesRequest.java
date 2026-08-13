package com.faithlog.shepherd.controller.dto.request;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.shepherd.service.command.ReplaceShepherdGroupAssigneesCommand;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReplaceShepherdGroupAssigneesRequest(
	@NotNull List<Long> assigneeUserIds
) {

	public ReplaceShepherdGroupAssigneesCommand toCommand(Long campusId, Long groupId, AuthenticatedUser authenticatedUser) {
		return new ReplaceShepherdGroupAssigneesCommand(campusId, groupId, authenticatedUser.userId(), assigneeUserIds);
	}
}
