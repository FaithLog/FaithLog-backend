package com.faithlog.poll.infrastructure.repository;

import com.faithlog.poll.domain.entity.PollResponseOption;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollResponseOptionRepository extends JpaRepository<PollResponseOption, Long> {

	List<PollResponseOption> findByResponseIdOrderByIdAsc(Long responseId);

	List<PollResponseOption> findByResponseIdIn(Collection<Long> responseIds);

	List<PollResponseOption> findByResponseIdInOrderByIdAsc(Collection<Long> responseIds);

	@Modifying(flushAutomatically = true)
	@Query("delete from PollResponseOption option where option.responseId = :responseId")
	void deleteByResponseId(@Param("responseId") Long responseId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		delete from PollResponseOption option
		where option.responseId in (
			select response.id
			from PollResponse response
			where response.pollId in :pollIds
		)
		""")
	int deleteByPollIdIn(@Param("pollIds") Collection<Long> pollIds);
}
