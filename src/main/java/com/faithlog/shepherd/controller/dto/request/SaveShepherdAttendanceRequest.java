package com.faithlog.shepherd.controller.dto.request;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.shepherd.service.command.SaveShepherdAttendanceCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SaveShepherdAttendanceRequest(
	@NotNull @PositiveOrZero Integer smallGroupMeetingCount,
	@NotNull @PositiveOrZero Integer holyWaveCount,
	@NotNull @PositiveOrZero Integer otherWorshipCount,
	@Size(max = 500) String note,
	@NotNull String status,
	@NotNull @PositiveOrZero Integer version
) {

	public SaveShepherdAttendanceCommand toCommand(
		Long campusId,
		Long groupId,
		LocalDate serviceDate,
		AuthenticatedUser authenticatedUser
	) {
		return new SaveShepherdAttendanceCommand(
			campusId,
			groupId,
			serviceDate,
			authenticatedUser.userId(),
			smallGroupMeetingCount,
			holyWaveCount,
			otherWorshipCount,
			note,
			status,
			version
		);
	}
}
