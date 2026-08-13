package com.faithlog.shepherd.service.command;

import java.util.List;

public record ReplaceShepherdGroupAssigneesCommand(
	Long campusId,
	Long groupId,
	Long requesterId,
	List<Long> assigneeUserIds
) {
}
