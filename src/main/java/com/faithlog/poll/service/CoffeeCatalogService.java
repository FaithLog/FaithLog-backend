package com.faithlog.poll.service;

import com.faithlog.poll.service.result.CoffeeBrandResult;
import com.faithlog.poll.service.result.CoffeeMenuResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.infrastructure.repository.CoffeeBrandRepository;
import com.faithlog.poll.infrastructure.repository.CoffeeMenuCatalogRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoffeeCatalogService {

	private final CoffeeBrandRepository coffeeBrandRepository;
	private final CoffeeMenuCatalogRepository coffeeMenuCatalogRepository;

	public CoffeeCatalogService(
		CoffeeBrandRepository coffeeBrandRepository,
		CoffeeMenuCatalogRepository coffeeMenuCatalogRepository
	) {
		this.coffeeBrandRepository = coffeeBrandRepository;
		this.coffeeMenuCatalogRepository = coffeeMenuCatalogRepository;
	}

	@Transactional(readOnly = true)
	public List<CoffeeBrandResult> listBrands() {
		return coffeeBrandRepository.findByIsActiveTrueOrderBySortOrderAscIdAsc()
			.stream()
			.map(CoffeeBrandResult::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<CoffeeMenuResult> listActiveMenus(Long brandId) {
		coffeeBrandRepository.findById(brandId)
			.filter(brand -> brand.isActive())
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_COFFEE_BRAND_NOT_FOUND));
		return coffeeMenuCatalogRepository.findByBrandIdAndIsActiveTrueOrderBySortOrderAscIdAsc(brandId)
			.stream()
			.map(CoffeeMenuResult::from)
			.toList();
	}
}
