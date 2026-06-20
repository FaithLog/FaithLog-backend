package com.faithlog.poll.infrastructure.jpa;

import com.faithlog.poll.domain.PollResponseOption;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollResponseOptionRepository extends JpaRepository<PollResponseOption, Long> {

	List<PollResponseOption> findByResponseIdOrderByIdAsc(Long responseId);

	List<PollResponseOption> findByResponseIdIn(Collection<Long> responseIds);

	void deleteByResponseId(Long responseId);
}
