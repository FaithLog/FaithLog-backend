package com.faithlog.poll.controller.dto.response;

import com.faithlog.poll.service.result.CoffeeMenuResult;

public record CoffeeMenuResponse(
	Long id,
	Long brandId,
	String menuCode,
	String name,
	int priceAmount,
	String category,
	int sortOrder
) {

	public static CoffeeMenuResponse from(CoffeeMenuResult result) {
		return new CoffeeMenuResponse(
			result.id(),
			result.brandId(),
			result.menuCode(),
			result.name(),
			result.priceAmount(),
			result.category(),
			result.sortOrder()
		);
	}
}
