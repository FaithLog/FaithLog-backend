package com.faithlog.shepherd.service.command;

import java.util.List;

public record CreateShepherdGroupCommand(
	Long campusId,
	Long requesterId,
	String name,
	List<Long> assigneeUserIds
) {
}
