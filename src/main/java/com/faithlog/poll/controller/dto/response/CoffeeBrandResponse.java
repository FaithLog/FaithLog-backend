package com.faithlog.poll.controller.dto.response;

import com.faithlog.poll.service.result.CoffeeBrandResult;

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
