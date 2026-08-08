package com.faithlog.poll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.service.port.PollPublishedEventPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PollStatusSynchronizerTest {

	private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");

	@Test
	void member_visibility_includes_exactly_seven_days_after_poll_end() {
		Poll poll = closedPollEndingAt(NOW.minusSeconds(7L * 24 * 60 * 60));
		PollStatusSynchronizer synchronizer = synchronizer();

		assertThat(synchronizer.isVisibleInWindow(poll, false)).isTrue();
	}

	@Test
	void member_visibility_excludes_time_after_seven_day_boundary() {
		Poll poll = closedPollEndingAt(NOW.minusSeconds(7L * 24 * 60 * 60 + 1));
		PollStatusSynchronizer synchronizer = synchronizer();

		assertThat(synchronizer.isVisibleInWindow(poll, false)).isFalse();
	}

	private PollStatusSynchronizer synchronizer() {
		return new PollStatusSynchronizer(
			Clock.fixed(NOW, ZoneOffset.UTC), mock(PollPublishedEventPort.class));
	}

	private Poll closedPollEndingAt(Instant endsAt) {
		Poll poll = mock(Poll.class);
		when(poll.status()).thenReturn(PollStatus.CLOSED);
		when(poll.endsAt()).thenReturn(endsAt);
		return poll;
	}
}
