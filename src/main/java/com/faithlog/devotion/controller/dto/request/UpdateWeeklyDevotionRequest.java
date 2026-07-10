package com.faithlog.devotion.controller.dto.request;

import com.faithlog.devotion.service.command.DevotionDailyCheckCommand;
import com.faithlog.devotion.service.command.UpdateWeeklyDevotionCommand;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record UpdateWeeklyDevotionRequest(
	@NotNull List<@Valid DailyCheckRequest> dailyChecks,
	int saturdayLateMinutes,
	boolean submit
) {

	public UpdateWeeklyDevotionCommand toCommand(
		Long campusId,
		AuthenticatedUser authenticatedUser,
		LocalDate weekStartDate
	) {
		return new UpdateWeeklyDevotionCommand(
			campusId,
			authenticatedUser.userId(),
			weekStartDate,
			dailyChecks.stream()
				.map(DailyCheckRequest::toCommand)
				.toList(),
			saturdayLateMinutes,
			submit
		);
	}

	public record DailyCheckRequest(
		@NotNull LocalDate recordDate,
		boolean quietTimeChecked,
		boolean prayerChecked,
		boolean bibleReadingChecked
	) {

		private DevotionDailyCheckCommand toCommand() {
			return new DevotionDailyCheckCommand(
				recordDate,
				quietTimeChecked,
				prayerChecked,
				bibleReadingChecked
			);
		}
	}
}
