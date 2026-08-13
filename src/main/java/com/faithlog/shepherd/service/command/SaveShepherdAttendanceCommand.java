package com.faithlog.shepherd.service.command;

import java.time.LocalDate;

public record SaveShepherdAttendanceCommand(
	Long campusId,
	Long groupId,
	LocalDate serviceDate,
	Long requesterId,
	Integer smallGroupMeetingCount,
	Integer holyWaveCount,
	Integer otherWorshipCount,
	String note,
	String status,
	Integer version
) {
}
