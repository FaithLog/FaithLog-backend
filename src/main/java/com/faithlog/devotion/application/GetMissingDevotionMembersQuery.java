package com.faithlog.devotion.application;

import java.time.LocalDate;

public record GetMissingDevotionMembersQuery(
	Long campusId,
	Long requesterId,
	LocalDate weekStartDate
) {
}
