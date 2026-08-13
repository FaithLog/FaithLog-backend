package com.faithlog.shepherd.service.result;

public record ShepherdAttendanceSummaryRow(
	long totalGroupCount,
	long submittedCount,
	long smallGroupMeetingCount,
	long holyWaveCount,
	long otherWorshipCount
) {
}
