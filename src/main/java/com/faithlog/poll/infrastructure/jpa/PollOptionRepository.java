package com.faithlog.poll.infrastructure.jpa;

import com.faithlog.poll.domain.PollOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {

	List<PollOption> findByPollIdOrderBySortOrderAsc(Long pollId);
}
