package com.faithlog.poll.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.PollStatus;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
class PollStatusSynchronizer {

	void openIfCurrent(Poll poll) {
		Instant now = Instant.now();
		if (!now.isBefore(poll.startsAt()) && now.isBefore(poll.endsAt())) {
			poll.open();
		}
	}

	void openScheduledPollIfCurrent(Poll poll) {
		Instant now = Instant.now();
		if (poll.status() == PollStatus.SCHEDULED && !now.isBefore(poll.startsAt()) && now.isBefore(poll.endsAt())) {
			poll.open();
		}
	}

	boolean isVisibleInWindow(Poll poll, boolean adminWindow) {
		Instant now = Instant.now();
		if (poll.status() == PollStatus.OPEN && !now.isBefore(poll.startsAt()) && now.isBefore(poll.endsAt())) {
			return true;
		}
		if (now.isBefore(poll.endsAt())) {
			return false;
		}
		Duration window = adminWindow ? Duration.ofDays(7) : Duration.ofDays(3);
		return !now.isAfter(poll.endsAt().plus(window));
	}

	void requireOpenPoll(Poll poll) {
		openScheduledPollIfCurrent(poll);
		Instant now = Instant.now();
		if (poll.status() != PollStatus.OPEN || now.isBefore(poll.startsAt()) || !now.isBefore(poll.endsAt())) {
			throw new BusinessException(ErrorCode.POLL_CLOSED);
		}
	}
}
