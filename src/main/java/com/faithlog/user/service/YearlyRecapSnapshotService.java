package com.faithlog.user.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.entity.YearlyRecapCampusSnapshot;
import com.faithlog.user.domain.entity.YearlyRecapSnapshot;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.infrastructure.repository.YearlyRecapCampusSnapshotRepository;
import com.faithlog.user.infrastructure.repository.YearlyRecapSnapshotRepository;
import com.faithlog.user.service.policy.YearlyRecapPeriod;
import com.faithlog.user.service.port.YearlyRecapAggregateQueryPort;
import com.faithlog.user.service.result.YearlyRecapSnapshotData;
import com.faithlog.user.service.result.YearlyRecapStoredSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class YearlyRecapSnapshotService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final UserRepository userRepository;
	private final YearlyRecapSnapshotRepository snapshotRepository;
	private final YearlyRecapCampusSnapshotRepository campusSnapshotRepository;
	private final YearlyRecapAggregateQueryPort aggregateQueryPort;
	private final YearlyRecapSnapshotAssembler assembler;
	private final Clock clock;

	public YearlyRecapSnapshotService(
		UserRepository userRepository,
		YearlyRecapSnapshotRepository snapshotRepository,
		YearlyRecapCampusSnapshotRepository campusSnapshotRepository,
		YearlyRecapAggregateQueryPort aggregateQueryPort,
		YearlyRecapSnapshotAssembler assembler,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.snapshotRepository = snapshotRepository;
		this.campusSnapshotRepository = campusSnapshotRepository;
		this.aggregateQueryPort = aggregateQueryPort;
		this.assembler = assembler;
		this.clock = clock;
	}

	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public YearlyRecapStoredSnapshot getOrCreate(Long userId, YearlyRecapPeriod period) {
		requireActiveUser(userRepository.findById(userId).orElse(null));
		YearlyRecapSnapshot existing = snapshotRepository
			.findByUserIdAndRecapYear(userId, period.recapYear())
			.orElse(null);
		if (existing != null) {
			return stored(existing);
		}

		requireActiveUser(userRepository.findByIdForUpdate(userId).orElse(null));
		existing = snapshotRepository.findByUserIdAndRecapYear(userId, period.recapYear()).orElse(null);
		if (existing != null) {
			return stored(existing);
		}

		Instant startInclusive = period.yearStart().atStartOfDay(SEOUL).toInstant();
		Instant endExclusive = period.yearEndExclusive().atStartOfDay(SEOUL).toInstant();
		YearlyRecapSnapshotData data = assembler.assemble(
			period.recapYear(),
			aggregateQueryPort.findActiveCampuses(userId, period.yearEndExclusive()),
			aggregateQueryPort.findDevotion(userId, period.yearStart(), period.yearEndExclusive()),
			aggregateQueryPort.findPrayer(userId, period.yearStart(), period.yearEndExclusive()),
			aggregateQueryPort.findPoll(userId, startInclusive, endExclusive)
		);
		YearlyRecapSnapshot saved = snapshotRepository.saveAndFlush(
			YearlyRecapSnapshot.create(userId, data, clock.instant())
		);
		List<YearlyRecapCampusSnapshot> campusSnapshots = data.campuses().stream()
			.map(campus -> YearlyRecapCampusSnapshot.create(saved.id(), campus))
			.toList();
		if (!campusSnapshots.isEmpty()) {
			campusSnapshotRepository.saveAll(campusSnapshots);
		}
		return new YearlyRecapStoredSnapshot(data, null);
	}

	private YearlyRecapStoredSnapshot stored(YearlyRecapSnapshot snapshot) {
		List<YearlyRecapCampusSnapshot> campuses = campusSnapshotRepository
			.findByYearlyRecapSnapshotIdOrderByCampusIdAsc(snapshot.id());
		return new YearlyRecapStoredSnapshot(snapshot.toData(campuses), snapshot.firstPresentedAt());
	}

	private void requireActiveUser(User user) {
		if (user == null || !user.isActive()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
	}
}
