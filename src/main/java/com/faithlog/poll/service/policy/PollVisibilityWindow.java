package com.faithlog.poll.service.policy;

import java.time.Duration;

public final class PollVisibilityWindow {

	public static final Duration MEMBER_AFTER_END = Duration.ofDays(7);
	public static final Duration ADMIN_AFTER_END = Duration.ofDays(7);

	private PollVisibilityWindow() {
	}

	public static Duration forViewer(boolean adminWindow) {
		return adminWindow ? ADMIN_AFTER_END : MEMBER_AFTER_END;
	}
}
