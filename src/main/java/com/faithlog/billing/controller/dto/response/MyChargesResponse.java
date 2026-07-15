package com.faithlog.billing.controller.dto.response;

import com.faithlog.billing.service.result.MyChargesResult;
import java.util.List;

public record MyChargesResponse(
	Long campusId,
	String campusName,
	String region,
	ChargeAmountSummaryResponse summary,
	List<ChargeListItemResponse> items,
	int page,
	int size,
	long totalElements,
	int totalPages
) {

	public static MyChargesResponse from(MyChargesResult result) {
		return new MyChargesResponse(
			result.campusId(),
			result.campusName(),
			result.region(),
			ChargeAmountSummaryResponse.from(result.summary()),
			result.items().stream().map(ChargeListItemResponse::from).toList(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages()
		);
	}
}
