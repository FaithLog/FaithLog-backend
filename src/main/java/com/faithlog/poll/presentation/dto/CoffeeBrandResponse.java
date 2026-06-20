package com.faithlog.poll.presentation.dto;

import com.faithlog.poll.application.CoffeeBrandResult;

public record CoffeeBrandResponse(
	Long id,
	String brandCode,
	String name,
	int sortOrder
) {

	public static CoffeeBrandResponse from(CoffeeBrandResult result) {
		return new CoffeeBrandResponse(result.id(), result.brandCode(), result.name(), result.sortOrder());
	}
}
