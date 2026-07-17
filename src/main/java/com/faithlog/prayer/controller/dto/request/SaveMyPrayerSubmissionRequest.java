package com.faithlog.prayer.controller.dto.request;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.prayer.service.command.SaveMyPrayerSubmissionCommand;
import java.time.LocalDate;

public record SaveMyPrayerSubmissionRequest(
	String content
) {

	public SaveMyPrayerSubmissionCommand toCommand(Long campusId, LocalDate weekStartDate, AuthenticatedUser authenticatedUser) {
		return new SaveMyPrayerSubmissionCommand(campusId, weekStartDate, authenticatedUser.userId(), content);
	}
}
