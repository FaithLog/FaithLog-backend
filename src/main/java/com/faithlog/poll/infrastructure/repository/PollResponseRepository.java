package com.faithlog.poll.infrastructure.repository;

import com.faithlog.poll.domain.entity.PollResponse;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollResponseRepository extends JpaRepository<PollResponse, Long> {

	interface PollResponseCountProjection {

		Long getPollId();

		long getResponseCount();
	}

	Optional<PollResponse> findByPollIdAndUserId(Long pollId, Long userId);

	List<PollResponse> findByPollIdInAndUserId(Collection<Long> pollIds, Long userId);

	List<PollResponse> findByPollIdOrderByIdAsc(Long pollId);

	long countByPollId(Long pollId);

	long countByPollIdAndUserIdIn(Long pollId, Collection<Long> userIds);

	@Query("""
		select response.pollId as pollId, count(response.id) as responseCount
		from PollResponse response
		where response.pollId in :pollIds
		  and response.userId in :userIds
		group by response.pollId
		""")
	List<PollResponseCountProjection> countByPollIdInAndUserIdIn(
		@Param("pollIds") Collection<Long> pollIds,
		@Param("userIds") Collection<Long> userIds
	);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PollResponse response where response.pollId in :pollIds")
	int deleteByPollIdIn(@Param("pollIds") Collection<Long> pollIds);
}
