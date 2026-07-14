package com.faithlog.user.service;

import java.time.Instant;

public record AccountWithdrawalLockScope(
	Long userId,
	String passwordHash,
	boolean active,
	Instant deletedAt
) {
}
