package com.faithlog.shepherd.service.result;

import java.time.Instant;

public record ShepherdHomeGroupRow(
	Long groupId,
	String groupName,
	Long reportId,
	Integer smallGroupMeetingCount,
	Integer holyWaveCount,
	Integer otherWorshipCount,
	String note,
	String reportStatus,
	Integer reportVersion,
	Instant lastModifiedAt
) {
}
