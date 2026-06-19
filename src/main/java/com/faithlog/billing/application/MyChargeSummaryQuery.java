package com.faithlog.billing.application;

public record MyChargeSummaryQuery(
	Long campusId,
	Long requesterId,
	int year,
	int month
) {
}
