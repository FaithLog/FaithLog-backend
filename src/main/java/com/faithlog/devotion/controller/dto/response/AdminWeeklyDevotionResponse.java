package com.faithlog.devotion.controller.dto.response;

import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.devotion.service.result.AdminWeeklyDevotionResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminWeeklyDevotionResponse(
	LocalDate weekStartDate,
	LocalDate weekEndDate,
	int activeMemberCount,
	int submittedCount,
	int missingCount,
	long totalPenaltyAmount,
	List<SubmittedMemberResponse> submittedMembers,
	List<MissingMemberResponse> missingMembers
) {

	public static AdminWeeklyDevotionResponse from(AdminWeeklyDevotionResult result) {
		return new AdminWeeklyDevotionResponse(
			result.weekStartDate(),
			result.weekEndDate(),
			result.activeMemberCount(),
			result.submittedCount(),
			result.missingCount(),
			result.totalPenaltyAmount(),
			result.submittedMembers().stream().map(SubmittedMemberResponse::from).toList(),
			result.missingMembers().stream().map(MissingMemberResponse::from).toList()
		);
	}

	public record SubmittedMemberResponse(
		Long userId,
		String name,
		String email,
		int quietTimeCount,
		int bibleReadingCount,
		int prayerCount,
		int saturdayLateMinutes,
		Instant submittedAt,
		PenaltyResponse penalty,
		List<DailyCheckResponse> dailyChecks
	) {

		private static SubmittedMemberResponse from(AdminWeeklyDevotionResult.SubmittedMember member) {
			return new SubmittedMemberResponse(
				member.userId(),
				member.name(),
				member.email(),
				member.quietTimeCount(),
				member.bibleReadingCount(),
				member.prayerCount(),
				member.saturdayLateMinutes(),
				member.submittedAt(),
				member.penalty() == null ? null : PenaltyResponse.from(member.penalty()),
				member.dailyChecks().stream().map(DailyCheckResponse::from).toList()
			);
		}
	}

	public record MissingMemberResponse(Long userId, String name, String email) {

		private static MissingMemberResponse from(AdminWeeklyDevotionResult.MissingMember member) {
			return new MissingMemberResponse(member.userId(), member.name(), member.email());
		}
	}

	public record PenaltyResponse(Long chargeItemId, int amount, ChargeStatus status) {

		private static PenaltyResponse from(AdminWeeklyDevotionResult.Penalty penalty) {
			return new PenaltyResponse(penalty.chargeItemId(), penalty.amount(), penalty.status());
		}
	}

	public record DailyCheckResponse(
		Long id,
		LocalDate recordDate,
		boolean quietTimeChecked,
		boolean bibleReadingChecked,
		boolean prayerChecked
	) {

		private static DailyCheckResponse from(AdminWeeklyDevotionResult.DailyCheck dailyCheck) {
			return new DailyCheckResponse(
				dailyCheck.id(),
				dailyCheck.recordDate(),
				dailyCheck.quietTimeChecked(),
				dailyCheck.bibleReadingChecked(),
				dailyCheck.prayerChecked()
			);
		}
	}
}
