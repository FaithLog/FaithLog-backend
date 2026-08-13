package com.faithlog.shepherd.controller.dto.response;

import com.faithlog.shepherd.service.result.ShepherdGroupAssigneeResult;

public record ShepherdGroupAssigneeResponse(
	Long userId,
	String name,
	String email
) {

	static ShepherdGroupAssigneeResponse from(ShepherdGroupAssigneeResult result) {
		return new ShepherdGroupAssigneeResponse(result.userId(), result.name(), result.email());
	}
}
