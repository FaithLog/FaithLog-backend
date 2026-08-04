package com.faithlog.poll.infrastructure.repository;

import com.faithlog.poll.domain.entity.PollNotificationOutbox;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollNotificationOutboxRepository extends JpaRepository<PollNotificationOutbox, Long> {

	boolean existsByPollId(Long pollId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PollNotificationOutbox outbox where outbox.pollId in :pollIds")
	int deleteByPollIdIn(@Param("pollIds") Collection<Long> pollIds);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select outbox from PollNotificationOutbox outbox where outbox.id = :outboxId")
	Optional<PollNotificationOutbox> findByIdForUpdate(@Param("outboxId") Long outboxId);

	@Query("select outbox.id from PollNotificationOutbox outbox where outbox.processedAt is null order by outbox.id")
	List<Long> findPendingIds(Pageable pageable);
}
