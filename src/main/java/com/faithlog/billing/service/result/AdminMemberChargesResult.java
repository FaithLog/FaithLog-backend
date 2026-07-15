package com.faithlog.billing.service.result;

import java.util.List;

public record AdminMemberChargesResult(
	Long campusId,
	String campusName,
	String region,
	Long userId,
	String name,
	String email,
	ChargeAmountSummaryResult summary,
	List<ChargeListItemResult> items,
	int page,
	int size,
	long totalElements,
	int totalPages
) {
}
