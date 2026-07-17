package com.faithlog.poll.infrastructure.repository;

import com.faithlog.poll.domain.entity.PollTemplateOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollTemplateOptionRepository extends JpaRepository<PollTemplateOption, Long> {

	List<PollTemplateOption> findByTemplateIdOrderBySortOrderAsc(Long templateId);

	void deleteByTemplateId(Long templateId);
}
