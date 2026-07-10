package com.faithlog.poll.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollComment;
import com.faithlog.poll.infrastructure.repository.PollCommentRepository;
import com.faithlog.poll.service.command.CreatePollCommentCommand;
import com.faithlog.poll.service.command.DeletePollCommentCommand;
import com.faithlog.poll.service.command.UpdatePollCommentCommand;
import com.faithlog.poll.service.result.PollCommentResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollCommentCommandService {

	private final PollCommentRepository pollCommentRepository;
	private final PollAccessService pollAccessService;
	private final PollLookupPolicy pollLookupPolicy;
	private final PollStatusSynchronizer pollStatusSynchronizer;

	public PollCommentCommandService(
		PollCommentRepository pollCommentRepository,
		PollAccessService pollAccessService,
		PollLookupPolicy pollLookupPolicy,
		PollStatusSynchronizer pollStatusSynchronizer
	) {
		this.pollCommentRepository = pollCommentRepository;
		this.pollAccessService = pollAccessService;
		this.pollLookupPolicy = pollLookupPolicy;
		this.pollStatusSynchronizer = pollStatusSynchronizer;
	}

	@Transactional
	public PollCommentResult createComment(CreatePollCommentCommand command) {
		pollAccessService.requireActiveCampusMember(command.campusId(), command.requesterId());
		Poll poll = pollLookupPolicy.getPollInCampus(command.campusId(), command.pollId());
		pollStatusSynchronizer.requireOpenPoll(poll);
		PollComment comment = pollCommentRepository.save(
			PollComment.create(poll.id(), command.requesterId(), command.content())
		);
		return PollCommentResult.of(comment, pollAccessService.getUser(command.requesterId()));
	}

	@Transactional
	public PollCommentResult updateComment(UpdatePollCommentCommand command) {
		pollAccessService.requirePollReader(command.campusId(), command.requesterId());
		Poll poll = pollLookupPolicy.getPollInCampus(command.campusId(), command.pollId());
		pollStatusSynchronizer.requireOpenPoll(poll);
		PollComment comment = getCommentInPoll(command.pollId(), command.commentId());
		requireCommentEditor(command.campusId(), command.requesterId(), comment);
		comment.update(command.content());
		return PollCommentResult.of(comment, pollAccessService.getUser(comment.userId()));
	}

	@Transactional
	public void deleteComment(DeletePollCommentCommand command) {
		pollAccessService.requirePollReader(command.campusId(), command.requesterId());
		Poll poll = pollLookupPolicy.getPollInCampus(command.campusId(), command.pollId());
		pollStatusSynchronizer.requireOpenPoll(poll);
		PollComment comment = getCommentInPoll(command.pollId(), command.commentId());
		requireCommentEditor(command.campusId(), command.requesterId(), comment);
		comment.delete();
	}

	private PollComment getCommentInPoll(Long pollId, Long commentId) {
		PollComment comment = pollCommentRepository.findById(commentId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_COMMENT_NOT_FOUND));
		if (!comment.pollId().equals(pollId)) {
			throw new BusinessException(ErrorCode.POLL_COMMENT_NOT_FOUND);
		}
		return comment;
	}

	private void requireCommentEditor(Long campusId, Long requesterId, PollComment comment) {
		if (comment.userId().equals(requesterId) || pollAccessService.hasAdminVisibility(campusId, requesterId)) {
			return;
		}
		throw new BusinessException(ErrorCode.POLL_COMMENT_FORBIDDEN);
	}
}
