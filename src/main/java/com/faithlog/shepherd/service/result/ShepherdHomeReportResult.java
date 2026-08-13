package com.faithlog.shepherd.service.result;

import java.time.Instant;

public record ShepherdHomeReportResult(
	Long reportId,
	int smallGroupMeetingCount,
	int holyWaveCount,
	int otherWorshipCount,
	String note,
	String status,
	int version,
	Instant lastModifiedAt
) {
}
