package com.faithlog.poll.infrastructure.jpa;

import com.faithlog.poll.domain.Poll;
import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.domain.PollType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollRepository extends JpaRepository<Poll, Long> {

	List<Poll> findByCampusIdOrderByIdDesc(Long campusId);

	List<Poll> findByCampusIdAndStatusOrderByIdAsc(Long campusId, PollStatus status);

	List<Poll> findByCampusIdAndStatusAndEndsAtBetweenOrderByIdAsc(
		Long campusId,
		PollStatus status,
		Instant startsAt,
		Instant endsAt
	);

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

	@Query("select poll.id from Poll poll where poll.endsAt < :endsAt")
	List<Long> findIdsByEndsAtBefore(@Param("endsAt") Instant endsAt);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Poll poll where poll.id in :pollIds")
	int deleteByIdIn(@Param("pollIds") List<Long> pollIds);
}
