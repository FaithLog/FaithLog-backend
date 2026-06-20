package com.faithlog.poll.infrastructure.jpa;

import com.faithlog.poll.domain.Poll;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollRepository extends JpaRepository<Poll, Long> {

	List<Poll> findByCampusIdOrderByIdDesc(Long campusId);
}
