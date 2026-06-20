package com.faithlog.poll.infrastructure.jpa;

import com.faithlog.poll.domain.PollComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollCommentRepository extends JpaRepository<PollComment, Long> {

	List<PollComment> findByPollIdOrderByIdAsc(Long pollId);
}
