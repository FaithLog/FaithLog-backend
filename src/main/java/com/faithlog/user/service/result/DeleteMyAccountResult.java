package com.faithlog.user.service.result;

import java.time.Instant;

public record DeleteMyAccountResult(
	Instant deletedAt
) {
}
