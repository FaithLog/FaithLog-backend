package com.faithlog.poll.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollNotificationOutbox;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import com.faithlog.poll.infrastructure.repository.PollNotificationOutboxRepository;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PollPublishedEventAdapterTest {

	@Mock private PollNotificationOutboxRepository outboxes;
	@Mock private PollRepository polls;
	private PollPublishedEventAdapter adapter;
	private Poll poll;

	@BeforeEach
	void setUp() {
		adapter = new PollPublishedEventAdapter(outboxes, polls);
		poll = Poll.create(7L, null, "주간 투표", PollType.CUSTOM, SelectionType.SINGLE,
			false, false, ChargeGenerationType.NONE, null, null,
			Instant.parse("2026-08-03T00:00:00Z"), Instant.parse("2026-08-04T00:00:00Z"), 11L);
		ReflectionTestUtils.setField(poll, "id", 99L);
		when(polls.findByIdForUpdate(99L)).thenReturn(Optional.of(poll));
	}

	@Test
	void locks_poll_and_creates_exactly_one_outbox() {
		when(outboxes.existsByPollId(99L)).thenReturn(false);

		assertThat(adapter.recordOpened(poll, Instant.parse("2026-08-03T00:01:00Z"))).isTrue();

		verify(outboxes).save(org.mockito.ArgumentMatchers.argThat(candidate ->
			candidate.pollId().equals(99L)
				&& candidate.campusId().equals(7L)
				&& candidate.creatorId().equals(11L)
				&& candidate.pollTitle().equals("주간 투표")));
	}

	@Test
	void existing_poll_outbox_is_idempotent_after_the_same_row_lock() {
		when(outboxes.existsByPollId(99L)).thenReturn(true);

		assertThat(adapter.recordOpened(poll, Instant.parse("2026-08-03T00:01:00Z"))).isFalse();

		verify(outboxes, never()).save(org.mockito.ArgumentMatchers.any(PollNotificationOutbox.class));
	}
}
