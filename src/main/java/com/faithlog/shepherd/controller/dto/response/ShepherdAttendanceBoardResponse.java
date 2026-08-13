package com.faithlog.shepherd.controller.dto.response;

import com.faithlog.shepherd.service.result.ShepherdAttendanceBoardResult;
import java.time.LocalDate;
import java.util.List;

public record ShepherdAttendanceBoardResponse(
	Long campusId,
	LocalDate serviceDate,
	int page,
	int size,
	long totalElements,
	int totalPages,
	long totalSubmittedCount,
	long totalMissingCount,
	long totalSmallGroupMeetingCount,
	long totalHolyWaveCount,
	long totalOtherWorshipCount,
	List<ShepherdAttendanceBoardGroupResponse> groups
) {

	public static ShepherdAttendanceBoardResponse from(ShepherdAttendanceBoardResult result) {
		return new ShepherdAttendanceBoardResponse(
			result.campusId(),
			result.serviceDate(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages(),
			result.totalSubmittedCount(),
			result.totalMissingCount(),
			result.totalSmallGroupMeetingCount(),
			result.totalHolyWaveCount(),
			result.totalOtherWorshipCount(),
			result.groups().stream().map(ShepherdAttendanceBoardGroupResponse::from).toList()
		);
	}
}
