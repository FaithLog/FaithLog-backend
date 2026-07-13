package com.faithlog.devotion.service.query;

import java.time.LocalDate;

public record GetAdminWeeklyDevotionMembersQuery(
	Long campusId,
	Long requesterId,
	LocalDate weekStartDate
) {
}
