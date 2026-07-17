package com.faithlog.devotion.service.query;

import java.time.LocalDate;

public record GetMissingDevotionMembersQuery(
	Long campusId,
	Long requesterId,
	LocalDate weekStartDate
) {
}
