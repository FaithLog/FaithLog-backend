package com.faithlog.poll.infrastructure.jpa;

import com.faithlog.poll.domain.PollResponse;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollResponseRepository extends JpaRepository<PollResponse, Long> {

	Optional<PollResponse> findByPollIdAndUserId(Long pollId, Long userId);

	List<PollResponse> findByPollIdInAndUserId(Collection<Long> pollIds, Long userId);

	List<PollResponse> findByPollIdOrderByIdAsc(Long pollId);

	long countByPollId(Long pollId);

	long countByPollIdAndUserIdIn(Long pollId, Collection<Long> userIds);
}
