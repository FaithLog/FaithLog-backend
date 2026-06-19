package com.faithlog.poll.infrastructure.jpa;

import com.faithlog.poll.domain.CoffeeMenuCatalog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoffeeMenuCatalogRepository extends JpaRepository<CoffeeMenuCatalog, Long> {

	Optional<CoffeeMenuCatalog> findByBrandIdAndMenuCode(Long brandId, String menuCode);

	Optional<CoffeeMenuCatalog> findByMenuCode(String menuCode);

	List<CoffeeMenuCatalog> findByBrandIdAndIsActiveTrueOrderBySortOrderAscIdAsc(Long brandId);
}
