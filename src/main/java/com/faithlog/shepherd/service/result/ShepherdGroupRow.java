package com.faithlog.shepherd.service.result;

public record ShepherdGroupRow(
	Long groupId,
	Long campusId,
	String name,
	String status,
	int version
) {
}
