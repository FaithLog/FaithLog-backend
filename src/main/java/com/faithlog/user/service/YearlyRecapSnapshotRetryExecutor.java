package com.faithlog.user.service;

import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

@Component
public class YearlyRecapSnapshotRetryExecutor {

	private static final int MAX_ATTEMPTS = 3;
	private static final String SNAPSHOT_UNIQUE_CONSTRAINT = "uk_yearly_recap_snapshots_user_year";

	public <T> T execute(Supplier<T> operation) {
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				return operation.get();
			} catch (RuntimeException exception) {
				if (attempt == MAX_ATTEMPTS || !isRetryable(exception)) {
					throw exception;
				}
			}
		}
		throw new IllegalStateException("Unreachable yearly recap retry state");
	}

	private boolean isRetryable(RuntimeException exception) {
		return exception instanceof TransientDataAccessException
			|| exception instanceof DataIntegrityViolationException && containsSnapshotConstraint(exception);
	}

	private boolean containsSnapshotConstraint(Throwable throwable) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current.getMessage() != null && current.getMessage().contains(SNAPSHOT_UNIQUE_CONSTRAINT)) {
				return true;
			}
		}
		return false;
	}
}
