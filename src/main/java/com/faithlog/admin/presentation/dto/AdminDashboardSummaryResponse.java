package com.faithlog.admin.presentation.dto;

import com.faithlog.admin.application.AdminDashboardService.AdminDashboardSummaryResult;
import com.faithlog.admin.application.AdminDashboardService.CampusSummary;
import com.faithlog.admin.application.AdminDashboardService.ChargeCategorySummary;
import com.faithlog.admin.application.AdminDashboardService.ChargeSummary;
import com.faithlog.admin.application.AdminDashboardService.DevotionSummary;
import com.faithlog.admin.application.AdminDashboardService.MemberSummary;
import com.faithlog.admin.application.AdminDashboardService.PollSummary;
import com.faithlog.billing.domain.PaymentCategory;
import java.time.LocalDate;
import java.util.List;

public record AdminDashboardSummaryResponse(
	CampusResponse campus,
	MemberResponse members,
	DevotionResponse devotion,
	ChargeResponse charges,
	PollResponse polls
) {

	public static AdminDashboardSummaryResponse from(AdminDashboardSummaryResult result) {
		return new AdminDashboardSummaryResponse(
			CampusResponse.from(result.campus()),
			MemberResponse.from(result.members()),
			DevotionResponse.from(result.devotion()),
			ChargeResponse.from(result.charges()),
			PollResponse.from(result.polls())
		);
	}

	public record CampusResponse(Long campusId, String campusName, String region) {

		static CampusResponse from(CampusSummary summary) {
			return new CampusResponse(summary.campusId(), summary.campusName(), summary.region());
		}
	}

	public record MemberResponse(long activeCount, long inactiveCount, long adminCount) {

		static MemberResponse from(MemberSummary summary) {
			return new MemberResponse(summary.activeCount(), summary.inactiveCount(), summary.adminCount());
		}
	}

	public record DevotionResponse(
		LocalDate weekStartDate,
		long submittedCount,
		long missingCount,
		double submitRate
	) {

		static DevotionResponse from(DevotionSummary summary) {
			return new DevotionResponse(
				summary.weekStartDate(),
				summary.submittedCount(),
				summary.missingCount(),
				summary.submitRate()
			);
		}
	}

	public record ChargeResponse(long unpaidAmount, long unpaidMemberCount, List<ChargeCategoryResponse> byCategory) {

		static ChargeResponse from(ChargeSummary summary) {
			return new ChargeResponse(
				summary.unpaidAmount(),
				summary.unpaidMemberCount(),
				summary.byCategory().stream().map(ChargeCategoryResponse::from).toList()
			);
		}
	}

	public record ChargeCategoryResponse(PaymentCategory paymentCategory, long unpaidAmount) {

		static ChargeCategoryResponse from(ChargeCategorySummary summary) {
			return new ChargeCategoryResponse(summary.paymentCategory(), summary.unpaidAmount());
		}
	}

	public record PollResponse(
		long openCount,
		long recentlyClosedCount,
		long missingResponseCount,
		int recentlyClosedDays
	) {

		static PollResponse from(PollSummary summary) {
			return new PollResponse(
				summary.openCount(),
				summary.recentlyClosedCount(),
				summary.missingResponseCount(),
				summary.recentlyClosedDays()
			);
		}
	}
}
