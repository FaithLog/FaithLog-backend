package com.faithlog.user.service;

import org.springframework.stereotype.Service;

@Service
public class YearlyRecapPresentationCommandService {

	private final YearlyRecapPresentationTransactionService transactionService;
	private final YearlyRecapSnapshotRetryExecutor retryExecutor;

	public YearlyRecapPresentationCommandService(
		YearlyRecapPresentationTransactionService transactionService,
		YearlyRecapSnapshotRetryExecutor retryExecutor
	) {
		this.transactionService = transactionService;
		this.retryExecutor = retryExecutor;
	}

	public void markPresented(Long userId, int recapYear) {
		retryExecutor.execute(() -> {
			transactionService.markPresented(userId, recapYear);
			return null;
		});
	}
}
