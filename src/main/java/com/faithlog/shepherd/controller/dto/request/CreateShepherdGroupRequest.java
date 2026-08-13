package com.faithlog.shepherd.controller.dto.request;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.shepherd.service.command.CreateShepherdGroupCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateShepherdGroupRequest(
	@NotBlank @Size(max = 100) String name,
	List<Long> assigneeUserIds
) {

	public CreateShepherdGroupCommand toCommand(Long campusId, AuthenticatedUser authenticatedUser) {
		return new CreateShepherdGroupCommand(campusId, authenticatedUser.userId(), name, assigneeUserIds);
	}
}
