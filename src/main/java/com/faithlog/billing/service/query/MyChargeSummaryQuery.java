package com.faithlog.billing.service.query;

public record MyChargeSummaryQuery(
	Long campusId,
	Long requesterId,
	int year,
	int month
) {
}
