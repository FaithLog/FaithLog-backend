package com.faithlog.poll.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.service.port.PollPublishedEventPort;
import com.faithlog.poll.service.policy.PollVisibilityWindow;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
class PollStatusSynchronizer {
	private final Clock clock;
	private final PollPublishedEventPort publishedEvents;

	PollStatusSynchronizer(Clock clock, PollPublishedEventPort publishedEvents) {
		this.clock = clock;
		this.publishedEvents = publishedEvents;
	}

	void openIfCurrent(Poll poll) {
		Instant now = clock.instant();
		if (!now.isBefore(poll.startsAt()) && now.isBefore(poll.endsAt())) {
			poll.open();
			publishedEvents.recordOpened(poll, now);
		}
	}

	void openScheduledPollIfCurrent(Poll poll) {
		Instant now = clock.instant();
		if (poll.status() == PollStatus.SCHEDULED && !now.isBefore(poll.startsAt()) && now.isBefore(poll.endsAt())) {
			poll.open();
			publishedEvents.recordOpened(poll, now);
		}
	}

	boolean isVisibleInWindow(Poll poll, boolean adminWindow) {
		Instant now = clock.instant();
		if (poll.status() == PollStatus.OPEN && !now.isBefore(poll.startsAt()) && now.isBefore(poll.endsAt())) {
			return true;
		}
		if (now.isBefore(poll.endsAt())) {
			return false;
		}
		return !now.isAfter(poll.endsAt().plus(PollVisibilityWindow.forViewer(adminWindow)));
	}

	void requireOpenPoll(Poll poll) {
		openScheduledPollIfCurrent(poll);
		Instant now = clock.instant();
		if (poll.status() != PollStatus.OPEN || now.isBefore(poll.startsAt()) || !now.isBefore(poll.endsAt())) {
			throw new BusinessException(ErrorCode.POLL_CLOSED);
		}
	}
}
