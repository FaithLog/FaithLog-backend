package com.faithlog.poll.infrastructure.repository;

import com.faithlog.poll.domain.entity.PollTemplate;
import com.faithlog.poll.domain.type.PollType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollTemplateRepository extends JpaRepository<PollTemplate, Long> {

	Optional<PollTemplate> findByCampusIdAndPollTypeAndIsDefaultTrue(Long campusId, PollType pollType);

	List<PollTemplate> findByCampusIdAndIsActiveTrueOrderByIdAsc(Long campusId);

	List<PollTemplate> findByIsActiveTrueAndAutoCreateEnabledTrueOrderByIdAsc();
}
