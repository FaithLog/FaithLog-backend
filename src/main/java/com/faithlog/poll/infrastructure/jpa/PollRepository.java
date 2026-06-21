package com.faithlog.poll.infrastructure.jpa;

import com.faithlog.poll.domain.Poll;
import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.domain.PollType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollRepository extends JpaRepository<Poll, Long> {

	List<Poll> findByCampusIdOrderByIdDesc(Long campusId);

	Optional<Poll> findByIdAndCampusId(Long id, Long campusId);

	boolean existsByCampusIdAndTemplateIdAndStartsAtGreaterThanEqualAndStartsAtLessThan(
		Long campusId,
		Long templateId,
		Instant startInclusive,
		Instant endExclusive
	);

	List<Poll> findByPollTypeAndStatusAndEndsAtLessThanEqualOrderByIdAsc(
		PollType pollType,
		PollStatus status,
		Instant endsAt
	);

	List<Poll> findByStatusAndEndsAtBetweenOrderByIdAsc(
		PollStatus status,
		Instant startsAt,
		Instant endsAt
	);
}
