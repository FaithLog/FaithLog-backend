package com.faithlog.shepherd.controller.dto.response;

import com.faithlog.shepherd.service.result.ShepherdHomeReportResult;
import java.time.Instant;

public record ShepherdHomeReportResponse(
	Long reportId,
	int smallGroupMeetingCount,
	int holyWaveCount,
	int otherWorshipCount,
	String note,
	String status,
	int version,
	Instant lastModifiedAt
) {

	public static ShepherdHomeReportResponse from(ShepherdHomeReportResult result) {
		return new ShepherdHomeReportResponse(
			result.reportId(),
			result.smallGroupMeetingCount(),
			result.holyWaveCount(),
			result.otherWorshipCount(),
			result.note(),
			result.status(),
			result.version(),
			result.lastModifiedAt()
		);
	}
}
