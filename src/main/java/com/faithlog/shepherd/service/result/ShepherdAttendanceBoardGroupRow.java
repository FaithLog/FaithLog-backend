package com.faithlog.shepherd.service.result;

import java.time.Instant;
import java.time.LocalDate;

public record ShepherdAttendanceBoardGroupRow(
	Long groupId,
	Long campusId,
	String groupName,
	int groupVersion,
	Long reportId,
	LocalDate serviceDate,
	Integer smallGroupMeetingCount,
	Integer holyWaveCount,
	Integer otherWorshipCount,
	String note,
	String reportStatus,
	Long lastModifiedByUserId,
	String lastModifiedByName,
	Instant lastModifiedAt,
	Integer reportVersion
) {
}
