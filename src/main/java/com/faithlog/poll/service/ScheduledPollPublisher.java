package com.faithlog.poll.service;

import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.service.port.PollPublishedEventPort;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduledPollPublisher {

	private final PollRepository polls;
	private final PollPublishedEventPort events;

	public ScheduledPollPublisher(PollRepository polls, PollPublishedEventPort events) {
		this.polls = polls;
		this.events = events;
	}

	@Transactional
	public boolean publishIfDue(Long pollId, Instant now) {
		var poll = polls.findByIdForUpdate(pollId).orElse(null);
		if (poll == null || poll.status() != PollStatus.SCHEDULED
			|| poll.startsAt().isAfter(now) || !poll.endsAt().isAfter(now)) {
			return false;
		}
		poll.open();
		events.recordOpened(poll, now);
		return true;
	}
}
