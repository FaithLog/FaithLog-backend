package com.faithlog.poll.infrastructure.adapter;

import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollNotificationOutbox;
import com.faithlog.poll.infrastructure.repository.PollNotificationOutboxRepository;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.service.port.PollPublishedEventPort;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class PollPublishedEventAdapter implements PollPublishedEventPort {

	private final PollNotificationOutboxRepository outboxes;
	private final PollRepository polls;

	public PollPublishedEventAdapter(PollNotificationOutboxRepository outboxes, PollRepository polls) {
		this.outboxes = outboxes;
		this.polls = polls;
	}

	@Override
	public boolean recordOpened(Poll poll, Instant openedAt) {
		Poll lockedPoll = polls.findByIdForUpdate(poll.id())
			.orElseThrow(() -> new IllegalStateException("opened poll disappeared before outbox creation"));
		if (outboxes.existsByPollId(lockedPoll.id())) {
			return false;
		}
		outboxes.save(PollNotificationOutbox.create(
			lockedPoll.id(), lockedPoll.campusId(), lockedPoll.createdBy(), lockedPoll.pollType(), lockedPoll.title(), openedAt));
		return true;
	}
}
