package com.faithlog.poll.infrastructure.jpa;

import com.faithlog.poll.domain.PollResponseOption;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollResponseOptionRepository extends JpaRepository<PollResponseOption, Long> {

	List<PollResponseOption> findByResponseIdOrderByIdAsc(Long responseId);

	List<PollResponseOption> findByResponseIdIn(Collection<Long> responseIds);

	@Modifying(flushAutomatically = true)
	@Query("delete from PollResponseOption option where option.responseId = :responseId")
	void deleteByResponseId(@Param("responseId") Long responseId);
}
