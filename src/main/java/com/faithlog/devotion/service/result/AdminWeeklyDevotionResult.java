package com.faithlog.devotion.service.result;

import com.faithlog.billing.domain.type.ChargeStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminWeeklyDevotionResult(
	LocalDate weekStartDate,
	LocalDate weekEndDate,
	int activeMemberCount,
	int submittedCount,
	int missingCount,
	long totalPenaltyAmount,
	List<SubmittedMember> submittedMembers,
	List<MissingMember> missingMembers
) {

	public record SubmittedMember(
		Long userId,
		String name,
		String email,
		int quietTimeCount,
		int bibleReadingCount,
		int prayerCount,
		int saturdayLateMinutes,
		Instant submittedAt,
		Penalty penalty,
		List<DailyCheck> dailyChecks
	) {
	}

	public record MissingMember(
		Long userId,
		String name,
		String email
	) {
	}

	public record Penalty(
		Long chargeItemId,
		int amount,
		ChargeStatus status
	) {
	}

	public record DailyCheck(
		Long id,
		LocalDate recordDate,
		boolean quietTimeChecked,
		boolean bibleReadingChecked,
		boolean prayerChecked
	) {
	}
}
