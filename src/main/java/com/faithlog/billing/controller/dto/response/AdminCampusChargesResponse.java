package com.faithlog.billing.controller.dto.response;

import com.faithlog.billing.service.result.AdminCampusChargeMemberResult;
import com.faithlog.billing.service.result.AdminCampusChargesResult;
import java.util.List;

public record AdminCampusChargesResponse(
	Long campusId,
	String campusName,
	String region,
	ChargeAmountSummaryResponse summary,
	List<MemberChargeSummaryResponse> members,
	int page,
	int size,
	long totalElements,
	int totalPages
) {

	public static AdminCampusChargesResponse from(AdminCampusChargesResult result) {
		return new AdminCampusChargesResponse(
			result.campusId(),
			result.campusName(),
			result.region(),
			ChargeAmountSummaryResponse.from(result.summary()),
			result.members().stream().map(MemberChargeSummaryResponse::from).toList(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages()
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
