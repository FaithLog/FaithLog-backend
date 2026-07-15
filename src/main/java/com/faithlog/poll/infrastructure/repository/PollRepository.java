package com.faithlog.poll.infrastructure.repository;

import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.billing.domain.type.PaymentCategory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PollRepository extends JpaRepository<Poll, Long> {

	Page<Poll> findByCampusIdAndPollType(Long campusId, PollType pollType, Pageable pageable);

	Page<Poll> findByCampusIdAndPollTypeAndStatus(
		Long campusId,
		PollType pollType,
		PollStatus status,
		Pageable pageable
	);

	@Query("""
		select poll from Poll poll
		where poll.campusId = :campusId
			and poll.pollType = :pollType
			and (:status is null or poll.status = :status)
			and (
				:includeArchived = true
				or poll.status <> :closedStatus
				or poll.endsAt >= :closedCutoff
			)
		""")
	Page<Poll> searchManagementPolls(
		@Param("campusId") Long campusId,
		@Param("pollType") PollType pollType,
		@Param("status") PollStatus status,
		@Param("includeArchived") boolean includeArchived,
		@Param("closedStatus") PollStatus closedStatus,
		@Param("closedCutoff") Instant closedCutoff,
		Pageable pageable
	);

	List<Poll> findByCampusIdOrderByIdDesc(Long campusId);

	List<Poll> findByCampusIdAndStatusOrderByIdAsc(Long campusId, PollStatus status);

	List<Poll> findByCampusIdAndStatusAndEndsAtBetweenOrderByIdAsc(
		Long campusId,
		PollStatus status,
		Instant startsAt,
		Instant endsAt
	);

	Optional<Poll> findByIdAndCampusId(Long id, Long campusId);

	@Query("""
		select poll.campusId as campusId,
			poll.createdBy as createdBy,
			poll.pollType as pollType,
			poll.status as status,
			poll.chargeGenerationType as chargeGenerationType,
			poll.paymentCategory as paymentCategory
		from Poll poll
		where poll.id = :id
		""")
	Optional<PollLockScope> findLockScopeById(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select poll from Poll poll where poll.id = :id and poll.campusId = :campusId")
	Optional<Poll> findByIdAndCampusIdForUpdate(@Param("id") Long id, @Param("campusId") Long campusId);

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

	interface PollLockScope {

		Long getCampusId();

		Long getCreatedBy();

		PollType getPollType();

		PollStatus getStatus();

		ChargeGenerationType getChargeGenerationType();

		PaymentCategory getPaymentCategory();
	}
}
