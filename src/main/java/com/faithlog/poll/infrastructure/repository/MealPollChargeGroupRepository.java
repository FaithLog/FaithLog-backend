package com.faithlog.poll.infrastructure.repository;

import com.faithlog.poll.domain.entity.MealPollChargeGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPollChargeGroupRepository extends JpaRepository<MealPollChargeGroup, Long> {

	List<MealPollChargeGroup> findBySettlementIdOrderByIdAsc(Long settlementId);
}
