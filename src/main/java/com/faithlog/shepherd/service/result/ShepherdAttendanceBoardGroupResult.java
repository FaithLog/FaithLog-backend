package com.faithlog.shepherd.service.result;

import java.util.List;

public record ShepherdAttendanceBoardGroupResult(
	Long groupId,
	String groupName,
	int groupVersion,
	List<ShepherdGroupAssigneeResult> assignees,
	ShepherdAttendanceReportResult report
) {
}
