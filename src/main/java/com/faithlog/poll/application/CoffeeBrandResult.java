package com.faithlog.poll.application;

import com.faithlog.poll.domain.CoffeeBrand;

public record CoffeeBrandResult(
	Long id,
	String brandCode,
	String name,
	int sortOrder
) {

	public static CoffeeBrandResult from(CoffeeBrand brand) {
		return new CoffeeBrandResult(brand.id(), brand.brandCode(), brand.name(), brand.sortOrder());
	}
}
