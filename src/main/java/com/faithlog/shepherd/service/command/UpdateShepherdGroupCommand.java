package com.faithlog.shepherd.service.command;

public record UpdateShepherdGroupCommand(
	Long campusId,
	Long groupId,
	Long requesterId,
	String name,
	Integer version
) {
}
