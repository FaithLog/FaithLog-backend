package com.faithlog.poll.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import org.springframework.stereotype.Component;

@Component
class PollLookupPolicy {

	private final PollRepository pollRepository;
	private final PollAccessService pollAccessService;
	private final PollStatusSynchronizer pollStatusSynchronizer;

	PollLookupPolicy(
		PollRepository pollRepository,
		PollAccessService pollAccessService,
		PollStatusSynchronizer pollStatusSynchronizer
	) {
		this.pollRepository = pollRepository;
		this.pollAccessService = pollAccessService;
		this.pollStatusSynchronizer = pollStatusSynchronizer;
	}

	Poll getVisiblePoll(Long campusId, Long pollId, Long requesterId) {
		pollAccessService.requirePollReader(campusId, requesterId);
		Poll poll = getPollInCampus(campusId, pollId);
		pollStatusSynchronizer.openScheduledPollIfCurrent(poll);
		if (!pollStatusSynchronizer.isVisibleInWindow(
			poll,
			pollAccessService.hasAdminVisibility(campusId, requesterId)
		)) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		}
		return poll;
	}

	Poll getPollInCampus(Long campusId, Long pollId) {
		Poll poll = pollRepository.findById(pollId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_NOT_FOUND));
		if (!poll.campusId().equals(campusId)) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		}
		return poll;
	}
}
