package com.faithlog.poll.infrastructure.repository;

import com.faithlog.poll.domain.entity.MealPollSettlement;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPollSettlementRepository extends JpaRepository<MealPollSettlement, Long> {

	Optional<MealPollSettlement> findByPollId(Long pollId);

	boolean existsByPollId(Long pollId);

	List<MealPollSettlement> findByPollIdIn(Collection<Long> pollIds);
}
