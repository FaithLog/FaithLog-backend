package com.faithlog.billing.service.result;

import java.util.List;

public record MyChargeSummaryResult(
	Long campusId,
	String campusName,
	String region,
	Long userId,
	String name,
	int totalPaidAmount,
	int monthlyPaidAmount,
	int monthlyUnpaidAmount,
	int monthlyTotalChargeAmount,
	List<ChargeCategorySummaryResult> monthlyByCategory
) {
}
