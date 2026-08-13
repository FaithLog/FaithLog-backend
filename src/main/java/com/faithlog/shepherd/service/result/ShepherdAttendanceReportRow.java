package com.faithlog.shepherd.service.result;

import java.time.Instant;
import java.time.LocalDate;

public record ShepherdAttendanceReportRow(
	Long reportId,
	Long campusId,
	Long groupId,
	LocalDate serviceDate,
	int smallGroupMeetingCount,
	int holyWaveCount,
	int otherWorshipCount,
	String note,
	String status,
	Long lastModifiedByUserId,
	String lastModifiedByName,
	Instant lastModifiedAt,
	int version
) {
}
