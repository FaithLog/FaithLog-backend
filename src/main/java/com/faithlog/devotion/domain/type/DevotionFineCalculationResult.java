package com.faithlog.devotion.domain.type;

import java.util.List;

public record DevotionFineCalculationResult(
	int totalAmount,
	List<DevotionFineCalculationItemResult> items
) {
	public DevotionFineCalculationResult {
		items = List.copyOf(items);
	}
}
