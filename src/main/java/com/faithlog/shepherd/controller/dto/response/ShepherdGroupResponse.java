package com.faithlog.shepherd.controller.dto.response;

import com.faithlog.shepherd.service.result.ShepherdGroupResult;
import java.util.List;

public record ShepherdGroupResponse(
	Long groupId,
	Long campusId,
	String name,
	String status,
	int version,
	List<ShepherdGroupAssigneeResponse> assignees
) {

	public static ShepherdGroupResponse from(ShepherdGroupResult result) {
		return new ShepherdGroupResponse(
			result.groupId(),
			result.campusId(),
			result.name(),
			result.status(),
			result.version(),
			result.assignees().stream().map(ShepherdGroupAssigneeResponse::from).toList()
		);
	}
}
