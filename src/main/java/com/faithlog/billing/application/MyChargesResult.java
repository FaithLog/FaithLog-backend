package com.faithlog.billing.application;

import java.util.List;

public record MyChargesResult(
	Long campusId,
	String campusName,
	String region,
	ChargeAmountSummaryResult summary,
	List<ChargeListItemResult> items
) {
}
