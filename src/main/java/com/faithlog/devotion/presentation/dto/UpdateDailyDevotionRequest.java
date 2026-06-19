package com.faithlog.devotion.presentation.dto;

import com.faithlog.devotion.application.UpdateDailyDevotionCommand;
import com.faithlog.global.security.AuthenticatedUser;
import java.time.LocalDate;

public record UpdateDailyDevotionRequest(
	boolean quietTimeChecked,
	boolean prayerChecked,
	boolean bibleReadingChecked
) {

	public UpdateDailyDevotionCommand toCommand(
		Long campusId,
		AuthenticatedUser authenticatedUser,
		LocalDate recordDate
	) {
		return new UpdateDailyDevotionCommand(
			campusId,
			authenticatedUser.userId(),
			recordDate,
			quietTimeChecked,
			prayerChecked,
			bibleReadingChecked
		);
	}
}
