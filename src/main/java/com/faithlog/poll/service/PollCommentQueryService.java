package com.faithlog.poll.service;

import com.faithlog.poll.infrastructure.repository.PollCommentRepository;
import com.faithlog.poll.service.result.PollCommentResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollCommentQueryService {

	private final PollCommentRepository pollCommentRepository;
	private final PollAccessService pollAccessService;
	private final PollLookupPolicy pollLookupPolicy;

	public PollCommentQueryService(
		PollCommentRepository pollCommentRepository,
		PollAccessService pollAccessService,
		PollLookupPolicy pollLookupPolicy
	) {
		this.pollCommentRepository = pollCommentRepository;
		this.pollAccessService = pollAccessService;
		this.pollLookupPolicy = pollLookupPolicy;
	}

	@Transactional
	public List<PollCommentResult> listComments(Long campusId, Long pollId, Long requesterId) {
		pollLookupPolicy.getVisiblePoll(campusId, pollId, requesterId);
		return pollCommentRepository.findByPollIdOrderByIdAsc(pollId)
			.stream()
			.map(comment -> PollCommentResult.of(comment, pollAccessService.getUser(comment.userId())))
			.toList();
	}
}
