package com.faithlog.poll.infrastructure.repository;

import com.faithlog.poll.domain.entity.PollOption;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {

	List<PollOption> findByPollIdOrderBySortOrderAsc(Long pollId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PollOption option where option.pollId in :pollIds")
	int deleteByPollIdIn(@Param("pollIds") Collection<Long> pollIds);
}
