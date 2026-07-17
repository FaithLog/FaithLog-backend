package com.faithlog.poll.service.result;

import com.faithlog.poll.domain.entity.CoffeeMenuCatalog;

public record CoffeeMenuResult(
	Long id,
	Long brandId,
	String menuCode,
	String name,
	int priceAmount,
	String category,
	int sortOrder
) {

	public static CoffeeMenuResult from(CoffeeMenuCatalog menu) {
		return new CoffeeMenuResult(
			menu.id(),
			menu.brandId(),
			menu.menuCode(),
			menu.name(),
			menu.priceAmount(),
			menu.category(),
			menu.sortOrder()
		);
	}
}
