package com.faithlog.shepherd.service.result;

import java.util.List;

public record ShepherdGroupResult(
	Long groupId,
	Long campusId,
	String name,
	String status,
	int version,
	List<ShepherdGroupAssigneeResult> assignees
) {
}
