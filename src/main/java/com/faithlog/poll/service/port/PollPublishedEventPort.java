package com.faithlog.poll.service.port;

import com.faithlog.poll.domain.entity.Poll;
import java.time.Instant;

public interface PollPublishedEventPort {
	boolean recordOpened(Poll poll, Instant openedAt);
}
