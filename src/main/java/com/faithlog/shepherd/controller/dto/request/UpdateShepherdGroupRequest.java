package com.faithlog.shepherd.controller.dto.request;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.shepherd.service.command.UpdateShepherdGroupCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateShepherdGroupRequest(
	@NotBlank String name,
	@NotNull Integer version
) {

	public UpdateShepherdGroupRequest {
		name = normalizeName(name);
	}

	public UpdateShepherdGroupCommand toCommand(Long campusId, Long groupId, AuthenticatedUser authenticatedUser) {
		return new UpdateShepherdGroupCommand(campusId, groupId, authenticatedUser.userId(), name, version);
	}

	private static String normalizeName(String value) {
		return value == null ? null : value.trim().replaceAll("\\s+", " ");
	}
}
