package com.faithlog.user.service.result;

public record PenaltySummaryRecapResult(
	long totalCount,
	long totalAmount,
	long paidCount,
	long paidAmount,
	long unpaidCount,
	long unpaidAmount
) {
}
