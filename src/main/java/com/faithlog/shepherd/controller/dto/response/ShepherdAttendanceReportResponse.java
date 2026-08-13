package com.faithlog.shepherd.controller.dto.response;

import com.faithlog.shepherd.service.result.ShepherdAttendanceReportResult;
import java.time.Instant;
import java.time.LocalDate;

public record ShepherdAttendanceReportResponse(
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

	public static ShepherdAttendanceReportResponse from(ShepherdAttendanceReportResult result) {
		return new ShepherdAttendanceReportResponse(
			result.reportId(),
			result.campusId(),
			result.groupId(),
			result.serviceDate(),
			result.smallGroupMeetingCount(),
			result.holyWaveCount(),
			result.otherWorshipCount(),
			result.note(),
			result.status(),
			result.lastModifiedByUserId(),
			result.lastModifiedByName(),
			result.lastModifiedAt(),
			result.version()
		);
	}
}
