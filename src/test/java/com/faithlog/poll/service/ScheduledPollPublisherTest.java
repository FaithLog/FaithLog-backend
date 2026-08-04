package com.faithlog.poll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.service.port.PollPublishedEventPort;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScheduledPollPublisherTest {

	@Mock private PollRepository polls;
	@Mock private PollPublishedEventPort events;

	@Test
	void due_scheduled_poll_opens_and_records_publication_once() {
		Instant now = Instant.parse("2026-08-03T01:00:00Z");
		Poll poll = scheduledPoll(now.minusSeconds(1), now.plusSeconds(3600));
		when(polls.findByIdForUpdate(99L)).thenReturn(Optional.of(poll));

		boolean published = new ScheduledPollPublisher(polls, events).publishIfDue(99L, now);

		assertThat(published).isTrue();
		assertThat(poll.status()).isEqualTo(PollStatus.OPEN);
		verify(events).recordOpened(poll, now);
	}

	@Test
	void early_or_already_open_poll_does_not_record_publication() {
		Instant now = Instant.parse("2026-08-03T01:00:00Z");
		Poll poll = scheduledPoll(now.plusSeconds(1), now.plusSeconds(3600));
		when(polls.findByIdForUpdate(99L)).thenReturn(Optional.of(poll));

		assertThat(new ScheduledPollPublisher(polls, events).publishIfDue(99L, now)).isFalse();

		verify(events, never()).recordOpened(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	private Poll scheduledPoll(Instant startsAt, Instant endsAt) {
		Poll poll = Poll.create(7L, null, "예약 투표", PollType.CUSTOM, SelectionType.SINGLE,
			false, false, ChargeGenerationType.NONE, null, null, startsAt, endsAt, 11L);
		ReflectionTestUtils.setField(poll, "id", 99L);
		return poll;
	}
}
