package com.faithlog.shepherd.service.result;

import java.time.LocalDate;
import java.util.List;

public record ShepherdAttendanceBoardResult(
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
	List<ShepherdAttendanceBoardGroupResult> groups
) {
}
