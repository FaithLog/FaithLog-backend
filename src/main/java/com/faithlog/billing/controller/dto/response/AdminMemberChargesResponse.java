package com.faithlog.billing.controller.dto.response;

import com.faithlog.billing.service.result.AdminMemberChargesResult;
import java.util.List;

public record AdminMemberChargesResponse(
	Long campusId,
	String campusName,
	String region,
	Long userId,
	String name,
	String email,
	ChargeAmountSummaryResponse summary,
	List<ChargeListItemResponse> items,
	int page,
	int size,
	long totalElements,
	int totalPages
) {

	public static AdminMemberChargesResponse from(AdminMemberChargesResult result) {
		return new AdminMemberChargesResponse(
			result.campusId(),
			result.campusName(),
			result.region(),
			result.userId(),
			result.name(),
			result.email(),
			ChargeAmountSummaryResponse.from(result.summary()),
			result.items().stream().map(ChargeListItemResponse::from).toList(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages()
		);
	}
}
