package com.faithlog.weeklymaterial.service.result;

import java.time.LocalDate;

public record WeeklyMaterialWeekResult(LocalDate weekStartDate, WeeklyMaterialFileResult shepherdGuide,
	WeeklyMaterialFileResult sundaySharingSheet, WeeklyMaterialFileResult saturdayLeaderSharingSheet) {
}
