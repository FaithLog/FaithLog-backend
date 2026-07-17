package com.faithlog.admin.service.result;

import com.faithlog.billing.domain.type.PaymentCategory;
import java.time.LocalDate;
import java.util.List;

public record AdminDashboardSummaryResult(
	CampusSummary campus,
	MemberSummary members,
	DevotionSummary devotion,
	ChargeSummary charges,
	PollSummary polls
) {

	public record CampusSummary(Long campusId, String campusName, String region) {
	}

	public record MemberSummary(long activeCount, long inactiveCount, long adminCount) {
	}

	public record DevotionSummary(
		LocalDate weekStartDate,
		long submittedCount,
		long missingCount,
		double submitRate
	) {
	}

	public record ChargeSummary(long unpaidAmount, long unpaidMemberCount, List<ChargeCategorySummary> byCategory) {
	}

	public record ChargeCategorySummary(PaymentCategory paymentCategory, long unpaidAmount) {
	}

	public record PollSummary(
		long openCount,
		long recentlyClosedCount,
		long missingResponseCount,
		int recentlyClosedDays
	) {
	}
}
