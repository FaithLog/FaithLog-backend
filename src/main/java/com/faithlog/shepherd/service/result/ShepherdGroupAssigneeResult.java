package com.faithlog.shepherd.service.result;

public record ShepherdGroupAssigneeResult(
	Long userId,
	String name,
	String email
) {
}
