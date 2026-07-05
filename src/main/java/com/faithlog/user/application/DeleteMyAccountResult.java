package com.faithlog.user.application;

import java.time.Instant;

public record DeleteMyAccountResult(
	Instant deletedAt
) {
}
