package com.faithlog.devotion.infrastructure.jpa;

import com.faithlog.devotion.domain.PenaltyRule;
import com.faithlog.devotion.domain.PenaltyRuleType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PenaltyRuleRepository extends JpaRepository<PenaltyRule, Long> {

	List<PenaltyRule> findByCampusIdOrderByIdAsc(Long campusId);

	List<PenaltyRule> findByCampusIdAndRuleTypeAndIsActiveTrue(Long campusId, PenaltyRuleType ruleType);
}
