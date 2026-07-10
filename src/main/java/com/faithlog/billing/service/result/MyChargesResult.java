package com.faithlog.billing.service.result;

import java.util.List;

public record MyChargesResult(
	Long campusId,
	String campusName,
	String region,
	ChargeAmountSummaryResult summary,
	List<ChargeListItemResult> items
) {
}
