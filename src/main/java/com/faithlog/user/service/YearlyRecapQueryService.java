package com.faithlog.user.service;

import com.faithlog.user.service.policy.YearlyRecapPeriod;
import com.faithlog.user.service.policy.YearlyRecapPolicy;
import com.faithlog.user.service.result.YearlyRecapPresentationResult;
import com.faithlog.user.service.result.YearlyRecapResult;
import com.faithlog.user.service.result.YearlyRecapSnapshotData;
import com.faithlog.user.service.result.YearlyRecapStoredSnapshot;
import org.springframework.stereotype.Service;

@Service
public class YearlyRecapQueryService {

	private final YearlyRecapPolicy policy;
	private final YearlyRecapSnapshotService snapshotService;
	private final YearlyRecapSnapshotRetryExecutor retryExecutor;

	public YearlyRecapQueryService(
		YearlyRecapPolicy policy,
		YearlyRecapSnapshotService snapshotService,
		YearlyRecapSnapshotRetryExecutor retryExecutor
	) {
		this.policy = policy;
		this.snapshotService = snapshotService;
		this.retryExecutor = retryExecutor;
	}

	public YearlyRecapResult getPrevious(Long userId) {
		YearlyRecapPeriod period = policy.previousPeriod();
		YearlyRecapStoredSnapshot stored = retryExecutor.execute(() -> snapshotService.getOrCreate(userId, period));
		YearlyRecapSnapshotData data = stored.data();
		boolean hasData = data.hasRecapData();
		return new YearlyRecapResult(
			data.recapYear(),
			hasData,
			new YearlyRecapPresentationResult(
				hasData && stored.firstPresentedAt() == null,
				hasData && period.homeCardVisible(),
				period.homeCardVisibleUntil(),
				stored.firstPresentedAt()
			),
			data.campuses(),
			data.devotion(),
			data.prayerActivity(),
			data.commentActivity(),
			data.penaltySummary()
		);
	}
}
