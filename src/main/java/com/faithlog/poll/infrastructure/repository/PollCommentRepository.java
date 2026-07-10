package com.faithlog.poll.infrastructure.repository;

import com.faithlog.poll.domain.entity.PollComment;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollCommentRepository extends JpaRepository<PollComment, Long> {

	List<PollComment> findByPollIdOrderByIdAsc(Long pollId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PollComment comment where comment.pollId in :pollIds")
	int deleteByPollIdIn(@Param("pollIds") Collection<Long> pollIds);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		delete from PollComment comment
		where comment.deleted = true
			and comment.deletedAt < :deletedAt
		""")
	int deleteSoftDeletedBefore(@Param("deletedAt") Instant deletedAt);
}
