package com.faithlog.user.service.port;

import java.time.LocalDate;

public record CampusRecapActivity(
	Long campusId,
	String campusName,
	LocalDate joinedDate
) {
}
