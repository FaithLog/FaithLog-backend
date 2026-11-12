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
	private final PollLookupSupport pollLookupSupport;

	public PollCommentQueryService(
		PollCommentRepository pollCommentRepository,
		PollAccessService pollAccessService,
		PollLookupSupport pollLookupSupport
	) {
		this.pollCommentRepository = pollCommentRepository;
		this.pollAccessService = pollAccessService;
		this.pollLookupSupport = pollLookupSupport;
	}

	@Transactional
	public List<PollCommentResult> listComments(Long campusId, Long pollId, Long requesterId) {
		pollLookupSupport.getVisiblePoll(campusId, pollId, requesterId);
		var comments = pollCommentRepository.findByPollIdOrderByIdAsc(pollId);
		if (comments.isEmpty()) return List.of();
		var authors = pollAccessService.getUsers(comments.stream().map(comment -> comment.userId()).distinct().toList());
		return comments.stream()
			.map(comment -> PollCommentResult.of(comment, authors.get(comment.userId())))
			.toList();
	}
}
