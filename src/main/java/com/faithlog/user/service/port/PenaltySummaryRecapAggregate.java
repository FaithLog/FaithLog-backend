package com.faithlog.user.service.port;

public record PenaltySummaryRecapAggregate(
	long paidCount,
	long paidAmount,
	long unpaidCount,
	long unpaidAmount
) {
	public PenaltySummaryRecapAggregate {
		if (paidCount < 0 || paidAmount < 0 || unpaidCount < 0 || unpaidAmount < 0) {
			throw new IllegalArgumentException("penalty aggregate must be nonnegative");
		}
	}

	public long totalCount() {
		return Math.addExact(paidCount, unpaidCount);
	}

	public long totalAmount() {
		return Math.addExact(paidAmount, unpaidAmount);
	}
}
