package com.faithlog.poll.presentation;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.poll.application.CoffeeCatalogService;
import com.faithlog.poll.presentation.dto.CoffeeBrandResponse;
import com.faithlog.poll.presentation.dto.CoffeeMenuResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coffee-brands")
public class CoffeeCatalogController {

	private final CoffeeCatalogService coffeeCatalogService;

	public CoffeeCatalogController(CoffeeCatalogService coffeeCatalogService) {
		this.coffeeCatalogService = coffeeCatalogService;
	}

	@GetMapping
	public ApiResponse<List<CoffeeBrandResponse>> listBrands() {
		List<CoffeeBrandResponse> responses = coffeeCatalogService.listBrands()
			.stream()
			.map(CoffeeBrandResponse::from)
			.toList();
		return ApiResponse.success(responses);
	}

	@GetMapping("/{brandId}/menus")
	public ApiResponse<List<CoffeeMenuResponse>> listMenus(@PathVariable Long brandId) {
		List<CoffeeMenuResponse> responses = coffeeCatalogService.listActiveMenus(brandId)
			.stream()
			.map(CoffeeMenuResponse::from)
			.toList();
		return ApiResponse.success(responses);
	}
}
