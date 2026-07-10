package com.faithlog.poll.infrastructure.repository;

import com.faithlog.poll.domain.entity.CoffeeBrand;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoffeeBrandRepository extends JpaRepository<CoffeeBrand, Long> {

	Optional<CoffeeBrand> findByBrandCode(String brandCode);

	List<CoffeeBrand> findByIsActiveTrueOrderBySortOrderAscIdAsc();
}
