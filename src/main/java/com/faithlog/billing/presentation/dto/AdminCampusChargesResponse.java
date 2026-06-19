package com.faithlog.billing.presentation.dto;

import com.faithlog.billing.application.AdminCampusChargeMemberResult;
import com.faithlog.billing.application.AdminCampusChargesResult;
import java.util.List;

public record AdminCampusChargesResponse(
	Long campusId,
	String campusName,
	String region,
	ChargeAmountSummaryResponse summary,
	List<MemberChargeSummaryResponse> members
) {

	public static AdminCampusChargesResponse from(AdminCampusChargesResult result) {
		return new AdminCampusChargesResponse(
			result.campusId(),
			result.campusName(),
			result.region(),
			ChargeAmountSummaryResponse.from(result.summary()),
			result.members().stream().map(MemberChargeSummaryResponse::from).toList()
		);
	}

	public record MemberChargeSummaryResponse(
		Long userId,
		String name,
		String email,
		int totalAmount,
		int unpaidAmount,
		int paidAmount,
		int waivedAmount,
		int canceledAmount
	) {

		static MemberChargeSummaryResponse from(AdminCampusChargeMemberResult result) {
			return new MemberChargeSummaryResponse(
				result.userId(),
				result.name(),
				result.email(),
				result.totalAmount(),
				result.unpaidAmount(),
				result.paidAmount(),
				result.waivedAmount(),
				result.canceledAmount()
			);
		}
	}
}
