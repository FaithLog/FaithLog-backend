package com.faithlog.shepherd.controller.dto.response;

import com.faithlog.shepherd.service.result.ShepherdAttendanceBoardGroupResult;
import java.util.List;

public record ShepherdAttendanceBoardGroupResponse(
	Long groupId,
	String groupName,
	int groupVersion,
	List<ShepherdGroupAssigneeResponse> assignees,
	ShepherdAttendanceReportResponse report
) {

	static ShepherdAttendanceBoardGroupResponse from(ShepherdAttendanceBoardGroupResult result) {
		return new ShepherdAttendanceBoardGroupResponse(
			result.groupId(),
			result.groupName(),
			result.groupVersion(),
			result.assignees().stream().map(ShepherdGroupAssigneeResponse::from).toList(),
			result.report() == null ? null : ShepherdAttendanceReportResponse.from(result.report())
		);
	}
}
