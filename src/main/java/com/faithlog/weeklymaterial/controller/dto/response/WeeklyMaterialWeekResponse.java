package com.faithlog.weeklymaterial.controller.dto.response;

import com.faithlog.weeklymaterial.service.result.WeeklyMaterialWeekResult;
import java.time.LocalDate;

public record WeeklyMaterialWeekResponse(LocalDate weekStartDate, WeeklyMaterialFileResponse shepherdGuide,
	WeeklyMaterialFileResponse sharingSheet) {
	public static WeeklyMaterialWeekResponse from(WeeklyMaterialWeekResult result) {
		return new WeeklyMaterialWeekResponse(result.weekStartDate(),
			WeeklyMaterialFileResponse.from(result.shepherdGuide()),
			WeeklyMaterialFileResponse.from(result.sharingSheet()));
	}
}
