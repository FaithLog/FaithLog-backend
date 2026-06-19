package com.faithlog.billing.presentation.dto;

import com.faithlog.billing.application.MyChargesResult;
import java.util.List;

public record MyChargesResponse(
	Long campusId,
	String campusName,
	String region,
	ChargeAmountSummaryResponse summary,
	List<ChargeListItemResponse> items
) {

	public static MyChargesResponse from(MyChargesResult result) {
		return new MyChargesResponse(
			result.campusId(),
			result.campusName(),
			result.region(),
			ChargeAmountSummaryResponse.from(result.summary()),
			result.items().stream().map(ChargeListItemResponse::from).toList()
		);
	}
}
