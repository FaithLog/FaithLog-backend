package com.faithlog.user.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.YearlyRecapSnapshot;
import com.faithlog.user.infrastructure.repository.YearlyRecapSnapshotRepository;
import com.faithlog.user.service.policy.YearlyRecapPeriod;
import com.faithlog.user.service.policy.YearlyRecapPolicy;
import com.faithlog.user.service.result.YearlyRecapStoredSnapshot;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class YearlyRecapPresentationCommandService {

	private final YearlyRecapPolicy policy;
	private final YearlyRecapSnapshotService snapshotService;
	private final YearlyRecapSnapshotRepository snapshotRepository;
	private final Clock clock;

	public YearlyRecapPresentationCommandService(
		YearlyRecapPolicy policy,
		YearlyRecapSnapshotService snapshotService,
		YearlyRecapSnapshotRepository snapshotRepository,
		Clock clock
	) {
		this.policy = policy;
		this.snapshotService = snapshotService;
		this.snapshotRepository = snapshotRepository;
		this.clock = clock;
	}

	@Transactional
	public void markPresented(Long userId, int recapYear) {
		if (!policy.isCurrentPreviousYear(recapYear)) {
			throw new BusinessException(ErrorCode.USER_YEARLY_RECAP_INVALID_YEAR);
		}
		YearlyRecapPeriod period = policy.previousPeriod();
		YearlyRecapStoredSnapshot stored = snapshotService.getOrCreate(userId, period);
		if (!stored.data().hasRecapData()) {
			return;
		}
		YearlyRecapSnapshot snapshot = snapshotRepository
			.findByUserIdAndRecapYearForUpdate(userId, recapYear)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_YEARLY_RECAP_INVALID_YEAR));
		snapshot.markPresented(clock.instant());
	}
}
