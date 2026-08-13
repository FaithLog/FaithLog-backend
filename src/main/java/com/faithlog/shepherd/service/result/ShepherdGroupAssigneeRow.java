package com.faithlog.shepherd.service.result;

public record ShepherdGroupAssigneeRow(
	Long groupId,
	Long userId,
	String name,
	String email
) {
}
