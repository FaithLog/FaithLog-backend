package com.faithlog.devotion.domain;

import java.util.List;

public record DevotionFineCalculationResult(
	int totalAmount,
	List<DevotionFineCalculationItemResult> items
) {
	public DevotionFineCalculationResult {
		items = List.copyOf(items);
	}
}
