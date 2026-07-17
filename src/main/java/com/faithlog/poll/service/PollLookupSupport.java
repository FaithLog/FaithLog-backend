package com.faithlog.poll.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import org.springframework.stereotype.Component;

@Component
class PollLookupSupport {

	private final PollRepository pollRepository;
	private final PollAccessService pollAccessService;
	private final PollStatusSynchronizer pollStatusSynchronizer;

	PollLookupSupport(
		PollRepository pollRepository,
		PollAccessService pollAccessService,
		PollStatusSynchronizer pollStatusSynchronizer
	) {
		this.pollRepository = pollRepository;
		this.pollAccessService = pollAccessService;
		this.pollStatusSynchronizer = pollStatusSynchronizer;
	}

	Poll getVisiblePoll(Long campusId, Long pollId, Long requesterId) {
		return getVisiblePollWithAccess(campusId, pollId, requesterId).poll();
	}

	VisiblePollAccess getVisiblePollWithAccess(Long campusId, Long pollId, Long requesterId) {
		pollAccessService.requirePollReader(campusId, requesterId);
		Poll poll = getPollInCampus(campusId, pollId);
		pollStatusSynchronizer.openScheduledPollIfCurrent(poll);
		boolean adminVisibility = pollAccessService.hasAdminVisibility(campusId, requesterId);
		if (!pollStatusSynchronizer.isVisibleInWindow(poll, adminVisibility)) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		}
		return new VisiblePollAccess(poll, adminVisibility);
	}

	Poll getPollInCampus(Long campusId, Long pollId) {
		Poll poll = pollRepository.findById(pollId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_NOT_FOUND));
		if (!poll.campusId().equals(campusId)) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		}
		return poll;
	}

	PollRepository.PollLockScope getPollLockScopeInCampus(Long campusId, Long pollId) {
		PollRepository.PollLockScope scope = pollRepository.findLockScopeById(pollId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_NOT_FOUND));
		if (!scope.getCampusId().equals(campusId)) {
			throw new BusinessException(ErrorCode.POLL_NOT_FOUND);
		}
		return scope;
	}

	Poll getPollInCampusForUpdate(Long campusId, Long pollId) {
		return pollRepository.findByIdAndCampusIdForUpdate(pollId, campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_NOT_FOUND));
	}

	record VisiblePollAccess(Poll poll, boolean adminVisibility) {
	}
}
