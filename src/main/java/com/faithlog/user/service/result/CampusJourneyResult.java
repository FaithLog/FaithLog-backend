package com.faithlog.user.service.result;

import java.time.LocalDate;

public record CampusJourneyResult(
	Long campusId,
	String campusName,
	LocalDate joinedDate,
	boolean joinedDuringRecapYear
) {
}
