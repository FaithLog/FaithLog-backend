package com.faithlog.poll.infrastructure.seed;

import com.faithlog.poll.domain.CoffeeBrand;
import com.faithlog.poll.domain.CoffeeMenuCatalog;
import com.faithlog.poll.infrastructure.jpa.CoffeeBrandRepository;
import com.faithlog.poll.infrastructure.jpa.CoffeeMenuCatalogRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ComposeCoffeeCatalogSeedRunner implements ApplicationRunner {

	static final String BRAND_CODE = "COMPOSE_COFFEE";
	private static final String BRAND_NAME = "Compose Coffee";
	private static final String SEED_PATH = "seed/compose-coffee-menu-2026.csv";

	private final CoffeeBrandRepository coffeeBrandRepository;
	private final CoffeeMenuCatalogRepository coffeeMenuCatalogRepository;

	public ComposeCoffeeCatalogSeedRunner(
		CoffeeBrandRepository coffeeBrandRepository,
		CoffeeMenuCatalogRepository coffeeMenuCatalogRepository
	) {
		this.coffeeBrandRepository = coffeeBrandRepository;
		this.coffeeMenuCatalogRepository = coffeeMenuCatalogRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		CoffeeBrand brand = coffeeBrandRepository.findByBrandCode(BRAND_CODE)
			.orElseGet(() -> coffeeBrandRepository.save(CoffeeBrand.create(BRAND_CODE, BRAND_NAME, 1)));
		brand.updateSeed(BRAND_NAME, 1, true);
		seedMenus(brand);
	}

	private void seedMenus(CoffeeBrand brand) {
		ClassPathResource resource = new ClassPathResource(SEED_PATH);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			String line = reader.readLine();
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] columns = line.split(",", -1);
				String menuCode = columns[0];
				String name = columns[1];
				int priceAmount = Integer.parseInt(columns[2]);
				String category = columns[3];
				int sortOrder = Integer.parseInt(columns[4]);
				boolean active = Boolean.parseBoolean(columns[5]);
				CoffeeMenuCatalog menu = coffeeMenuCatalogRepository.findByBrandIdAndMenuCode(brand.id(), menuCode)
					.orElseGet(() -> coffeeMenuCatalogRepository.save(CoffeeMenuCatalog.create(
						brand.id(),
						menuCode,
						name,
						priceAmount,
						category,
						sortOrder,
						active
					)));
				menu.updateSeed(name, priceAmount, category, sortOrder, active);
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Compose Coffee seed CSV를 읽을 수 없습니다.", exception);
		}
	}
}
