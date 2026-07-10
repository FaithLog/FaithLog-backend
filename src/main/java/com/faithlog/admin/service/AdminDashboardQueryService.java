package com.faithlog.admin.service;

import com.faithlog.admin.service.result.AdminDashboardSummaryResult;
import com.faithlog.admin.service.result.AdminDashboardSummaryResult.CampusSummary;
import com.faithlog.admin.service.result.AdminDashboardSummaryResult.ChargeCategorySummary;
import com.faithlog.admin.service.result.AdminDashboardSummaryResult.ChargeSummary;
import com.faithlog.admin.service.result.AdminDashboardSummaryResult.DevotionSummary;
import com.faithlog.admin.service.result.AdminDashboardSummaryResult.MemberSummary;
import com.faithlog.admin.service.result.AdminDashboardSummaryResult.PollSummary;
import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardQueryService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final int RECENTLY_CLOSED_DAYS = 7;
	private static final Set<CampusRole> DASHBOARD_ADMIN_ROLES = Set.of(
		CampusRole.MINISTER,
		CampusRole.ELDER,
		CampusRole.CAMPUS_LEADER
	);

	private final UserRepository userRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepository campusMemberRepository;
	private final WeeklyDevotionRecordRepository weeklyDevotionRecordRepository;
	private final ChargeItemRepository chargeItemRepository;
	private final PollRepository pollRepository;
	private final PollResponseRepository pollResponseRepository;

	public AdminDashboardQueryService(
		UserRepository userRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepository campusMemberRepository,
		WeeklyDevotionRecordRepository weeklyDevotionRecordRepository,
		ChargeItemRepository chargeItemRepository,
		PollRepository pollRepository,
		PollResponseRepository pollResponseRepository
	) {
		this.userRepository = userRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.weeklyDevotionRecordRepository = weeklyDevotionRecordRepository;
		this.chargeItemRepository = chargeItemRepository;
		this.pollRepository = pollRepository;
		this.pollResponseRepository = pollResponseRepository;
	}

	@Transactional(readOnly = true)
	public AdminDashboardSummaryResult getSummary(Long campusId, Long requesterId, LocalDate weekStartDate) {
		LocalDate targetWeekStartDate = resolveWeekStartDate(weekStartDate);
		User requester = userRepository.findById(requesterId)
			.filter(User::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		Campus campus = campusRepository.findById(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
		requireDashboardAccess(campus.id(), requester);

		List<CampusMember> activeMembers = campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(
			campus.id(),
			CampusMemberStatus.ACTIVE
		);
		List<CampusMember> allMembers = campusMemberRepository.findByCampusIdOrderByIdAsc(campus.id());
		return new AdminDashboardSummaryResult(
			new CampusSummary(campus.id(), campus.name(), campus.region()),
			memberSummary(activeMembers, allMembers),
			devotionSummary(campus.id(), activeMembers, targetWeekStartDate),
			chargeSummary(campus.id()),
			pollSummary(campus.id(), activeMembers)
		);
	}

	private LocalDate resolveWeekStartDate(LocalDate weekStartDate) {
		LocalDate resolved = weekStartDate == null
			? LocalDate.now(SEOUL_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
			: weekStartDate;
		if (resolved.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new BusinessException(ErrorCode.DEVOTION_INVALID_WEEK_START_DATE);
		}
		return resolved;
	}

	private void requireDashboardAccess(Long campusId, User requester) {
		if (requester.isAdmin()) {
			return;
		}
		CampusMember membership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.id())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_DASHBOARD_ACCESS_FORBIDDEN));
		if (!DASHBOARD_ADMIN_ROLES.contains(membership.campusRole())) {
			throw new BusinessException(ErrorCode.ADMIN_DASHBOARD_ACCESS_FORBIDDEN);
		}
	}

	private MemberSummary memberSummary(List<CampusMember> activeMembers, List<CampusMember> allMembers) {
		long inactiveCount = allMembers.stream()
			.filter(member -> member.status() != CampusMemberStatus.ACTIVE)
			.count();
		long adminCount = activeMembers.stream()
			.filter(member -> DASHBOARD_ADMIN_ROLES.contains(member.campusRole()))
			.count();
		return new MemberSummary(activeMembers.size(), inactiveCount, adminCount);
	}

	private DevotionSummary devotionSummary(Long campusId, List<CampusMember> activeMembers, LocalDate weekStartDate) {
		Set<Long> activeUserIds = activeMembers.stream().map(CampusMember::userId).collect(Collectors.toSet());
		long submittedCount = weeklyDevotionRecordRepository.findByCampusIdAndWeekStartDate(campusId, weekStartDate)
			.stream()
			.filter(record -> activeUserIds.contains(record.userId()))
			.filter(record -> record.submittedAt() != null)
			.map(WeeklyDevotionRecord::userId)
			.distinct()
			.count();
		long activeCount = activeMembers.size();
		long missingCount = activeCount - submittedCount;
		double submitRate = activeCount == 0 ? 0.0 : BigDecimal.valueOf(submittedCount)
			.multiply(BigDecimal.valueOf(100))
			.divide(BigDecimal.valueOf(activeCount), 1, RoundingMode.HALF_UP)
			.doubleValue();
		return new DevotionSummary(weekStartDate, submittedCount, missingCount, submitRate);
	}

	private ChargeSummary chargeSummary(Long campusId) {
		List<ChargeItem> unpaidCharges = chargeItemRepository.findByCampusIdAndStatus(campusId, ChargeStatus.UNPAID);
		long unpaidAmount = unpaidCharges.stream().mapToLong(ChargeItem::amount).sum();
		long unpaidMemberCount = unpaidCharges.stream().map(ChargeItem::userId).distinct().count();
		Map<PaymentCategory, Long> amountByCategory = new EnumMap<>(PaymentCategory.class);
		for (PaymentCategory paymentCategory : PaymentCategory.values()) {
			amountByCategory.put(paymentCategory, 0L);
		}
		unpaidCharges.forEach(charge -> amountByCategory.merge(
			charge.paymentCategory(),
			(long) charge.amount(),
			Long::sum
		));
		List<ChargeCategorySummary> byCategory = amountByCategory.entrySet()
			.stream()
			.sorted(Comparator.comparingInt(entry -> entry.getKey().ordinal()))
			.map(entry -> new ChargeCategorySummary(entry.getKey(), entry.getValue()))
			.toList();
		return new ChargeSummary(unpaidAmount, unpaidMemberCount, byCategory);
	}

	private PollSummary pollSummary(Long campusId, List<CampusMember> activeMembers) {
		Instant now = Instant.now();
		List<Poll> openPolls = pollRepository.findByCampusIdAndStatusOrderByIdAsc(campusId, PollStatus.OPEN);
		long recentlyClosedCount = pollRepository.findByCampusIdAndStatusAndEndsAtBetweenOrderByIdAsc(
			campusId,
			PollStatus.CLOSED,
			now.minusSeconds(RECENTLY_CLOSED_DAYS * 24L * 60L * 60L),
			now
		).size();
		Set<Long> activeUserIds = activeMembers.stream().map(CampusMember::userId).collect(Collectors.toSet());
		long missingResponseCount = activeUserIds.isEmpty()
			? 0
			: openPolls.stream().mapToLong(poll -> Math.max(
				0,
				activeUserIds.size() - pollResponseRepository.countByPollIdAndUserIdIn(poll.id(), activeUserIds)
			)).sum();
		return new PollSummary(openPolls.size(), recentlyClosedCount, missingResponseCount, RECENTLY_CLOSED_DAYS);
	}
}
