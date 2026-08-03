package com.faithlog.poll.infrastructure.scheduler;

import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.infrastructure.repository.PollNotificationOutboxRepository;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.service.PollNotificationOutboxProcessor;
import com.faithlog.poll.service.ScheduledPollPublisher;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "faithlog.scheduler", name = "enabled", havingValue = "true")
public class PollPublicationScheduledJobs {

	private static final Logger log = LoggerFactory.getLogger(PollPublicationScheduledJobs.class);
	private static final int BATCH_SIZE = 100;

	private final PollRepository polls;
	private final ScheduledPollPublisher publisher;
	private final PollNotificationOutboxRepository outboxes;
	private final PollNotificationOutboxProcessor processor;
	private final Clock clock;

	public PollPublicationScheduledJobs(
		PollRepository polls,
		ScheduledPollPublisher publisher,
		PollNotificationOutboxRepository outboxes,
		PollNotificationOutboxProcessor processor,
		Clock clock
	) {
		this.polls = polls;
		this.publisher = publisher;
		this.outboxes = outboxes;
		this.processor = processor;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.poll-publish-delay-ms:60000}")
	public void publishDuePolls() {
		var now = clock.instant();
		polls.findDueIds(PollStatus.SCHEDULED, now, PageRequest.of(0, BATCH_SIZE))
			.forEach(id -> publisher.publishIfDue(id, now));
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.poll-outbox-delay-ms:60000}")
	public void deliverPollNotifications() {
		outboxes.findPendingIds(PageRequest.of(0, BATCH_SIZE)).forEach(id -> {
			try {
				processor.process(id);
			} catch (RuntimeException exception) {
				log.warn("Poll notification outbox processing will retry. outboxId={}", id);
			}
		});
	}
}
