package com.faithlog.billing.application;

import com.faithlog.billing.application.port.ChargeItemRepositoryPort;
import com.faithlog.billing.application.policy.BillingAccessPolicy;
import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.campus.application.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.application.port.CampusMemberRepositoryPort;
import com.faithlog.campus.application.port.CampusRepositoryPort;
import com.faithlog.campus.application.port.CampusUserLookupPort;
import com.faithlog.campus.application.port.CampusUserLookupResult;
import com.faithlog.campus.domain.Campus;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusMemberStatus;
import com.faithlog.campus.domain.DutyType;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingQueryService {

	private static final String MY_CHARGE_LIST_FORBIDDEN = "본인 청구 조회 권한이 없습니다.";
	private static final String ADMIN_CHARGE_LIST_FORBIDDEN = "캠퍼스 청구 조회 권한이 없습니다.";

	private final ChargeItemRepositoryPort chargeItemRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;

	public BillingQueryService(
		ChargeItemRepositoryPort chargeItemRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository
	) {
		this.chargeItemRepository = chargeItemRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
	}

	@Transactional(readOnly = true)
	public MyChargesResult listMyCharges(MyChargeListQuery query) {
		Campus campus = getCampus(query.campusId());
		CampusUserLookupResult requester = getActiveUser(query.requesterId());
		requireActiveCampusMember(query.campusId(), requester.userId(), MY_CHARGE_LIST_FORBIDDEN);

		Set<Long> userIds = Set.of(requester.userId());
		ChargeSearchCriteria criteria = new ChargeSearchCriteria(
			query.campusId(),
			userIds,
			query.paymentCategory(),
			query.status()
		);
		List<ChargeItem> summaryTargets = chargeItemRepository.searchCharges(criteria);
		Page<ChargeItem> page = chargeItemRepository.searchCharges(criteria, query.pageable());
		return new MyChargesResult(
			campus.id(),
			campus.name(),
			campus.region(),
			summarize(summaryTargets),
			page.stream().map(ChargeListItemResult::from).toList()
		);
	}

	@Transactional(readOnly = true)
	public MyChargeSummaryResult getMyChargeSummary(MyChargeSummaryQuery query) {
		Campus campus = getCampus(query.campusId());
		CampusUserLookupResult requester = getActiveUser(query.requesterId());
		requireActiveCampusMember(query.campusId(), requester.userId(), MY_CHARGE_LIST_FORBIDDEN);
		YearMonth yearMonth = yearMonth(query.year(), query.month());

		List<ChargeItem> charges = chargeItemRepository.searchCharges(new ChargeSearchCriteria(
			query.campusId(),
			Set.of(requester.userId()),
			null,
			null
		));
		List<ChargeItem> monthlyCreatedCharges = charges.stream()
			.filter(charge -> isInMonth(charge.createdAt(), yearMonth))
			.toList();

		int totalPaidAmount = charges.stream()
			.filter(charge -> charge.status() == ChargeStatus.PAID)
			.mapToInt(ChargeItem::amount)
			.sum();
		int monthlyPaidAmount = charges.stream()
			.filter(charge -> charge.status() == ChargeStatus.PAID)
			.filter(charge -> isInMonth(charge.paidAt(), yearMonth))
			.mapToInt(ChargeItem::amount)
			.sum();
		int monthlyUnpaidAmount = monthlyCreatedCharges.stream()
			.filter(charge -> charge.status() == ChargeStatus.UNPAID)
			.mapToInt(ChargeItem::amount)
			.sum();

		return new MyChargeSummaryResult(
			campus.id(),
			campus.name(),
			campus.region(),
			requester.userId(),
			requester.name(),
			totalPaidAmount,
			monthlyPaidAmount,
			monthlyUnpaidAmount,
			monthlyCreatedCharges.stream().mapToInt(ChargeItem::amount).sum(),
			monthlyByCategory(monthlyCreatedCharges)
		);
	}

	@Transactional(readOnly = true)
	public AdminCampusChargesResult listAdminCampusCharges(AdminCampusChargeListQuery query) {
		Campus campus = getCampus(query.campusId());
		requireCampusChargeManager(query.campusId(), query.requesterId(), query.paymentCategory());
		List<CampusUserLookupResult> targetUsers = targetUsers(query.campusId(), query.userId(), query.keyword());
		Set<Long> targetUserIds = targetUsers.stream()
			.map(CampusUserLookupResult::userId)
			.collect(Collectors.toSet());
		Map<Long, CampusUserLookupResult> usersById = targetUsers.stream()
			.collect(Collectors.toMap(CampusUserLookupResult::userId, Function.identity()));

		List<ChargeItem> charges = chargeItemRepository.searchCharges(new ChargeSearchCriteria(
			query.campusId(),
			targetUserIds,
			query.paymentCategory(),
			query.status()
		));
		List<AdminCampusChargeMemberResult> members = aggregateMembers(charges, usersById, query.pageable());
		return new AdminCampusChargesResult(
			campus.id(),
			campus.name(),
			campus.region(),
			summarize(charges),
			members
		);
	}

	@Transactional(readOnly = true)
	public AdminMemberChargesResult listAdminMemberCharges(AdminMemberChargeListQuery query) {
		Campus campus = getCampus(query.campusId());
		requireCampusChargeManager(query.campusId(), query.requesterId(), query.paymentCategory());
		requireActiveCampusMember(query.campusId(), query.userId(), ADMIN_CHARGE_LIST_FORBIDDEN);
		CampusUserLookupResult targetUser = getActiveUser(query.userId());

		ChargeSearchCriteria criteria = new ChargeSearchCriteria(
			query.campusId(),
			Set.of(query.userId()),
			query.paymentCategory(),
			query.status()
		);
		List<ChargeItem> summaryTargets = chargeItemRepository.searchCharges(criteria);
		Page<ChargeItem> page = chargeItemRepository.searchCharges(criteria, query.pageable());
		return new AdminMemberChargesResult(
			campus.id(),
			campus.name(),
			campus.region(),
			targetUser.userId(),
			targetUser.name(),
			targetUser.email(),
			summarize(summaryTargets),
			page.stream().map(ChargeListItemResult::from).toList()
		);
	}

	private List<CampusUserLookupResult> targetUsers(Long campusId, Long userId, String keyword) {
		return campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(campusId, CampusMemberStatus.ACTIVE)
			.stream()
			.map(CampusMember::userId)
			.filter(memberUserId -> userId == null || memberUserId.equals(userId))
			.map(this::getActiveUser)
			.filter(user -> keywordMatches(user, keyword))
			.toList();
	}

	private boolean keywordMatches(CampusUserLookupResult user, String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return true;
		}
		String lowered = keyword.toLowerCase(Locale.ROOT);
		return user.name().toLowerCase(Locale.ROOT).contains(lowered)
			|| user.email().toLowerCase(Locale.ROOT).contains(lowered);
	}

	private List<AdminCampusChargeMemberResult> aggregateMembers(
		List<ChargeItem> charges,
		Map<Long, CampusUserLookupResult> usersById,
		Pageable pageable
	) {
		Map<Long, List<ChargeItem>> chargesByUser = charges.stream()
			.collect(Collectors.groupingBy(ChargeItem::userId, LinkedHashMap::new, Collectors.toList()));
		List<AdminCampusChargeMemberResult> members = new ArrayList<>();
		for (Map.Entry<Long, List<ChargeItem>> entry : chargesByUser.entrySet()) {
			CampusUserLookupResult user = usersById.get(entry.getKey());
			if (user == null) {
				continue;
			}
			ChargeAmountSummaryResult summary = summarize(entry.getValue());
			Instant latestChargeCreatedAt = entry.getValue().stream()
				.map(ChargeItem::createdAt)
				.filter(Objects::nonNull)
				.max(Comparator.naturalOrder())
				.orElse(null);
			members.add(new AdminCampusChargeMemberResult(
				user.userId(),
				user.name(),
				user.email(),
				summary.totalAmount(),
				summary.unpaidAmount(),
				summary.paidAmount(),
				summary.waivedAmount(),
				summary.canceledAmount(),
				latestChargeCreatedAt
			));
		}
		members.sort(memberComparator(pageable.getSort()));
		return page(members, pageable);
	}

	private Comparator<AdminCampusChargeMemberResult> memberComparator(Sort sort) {
		Sort.Order order = sort.stream().findFirst().orElse(Sort.Order.desc("createdAt"));
		Comparator<AdminCampusChargeMemberResult> comparator = switch (order.getProperty()) {
			case "userId" -> Comparator.comparing(AdminCampusChargeMemberResult::userId);
			case "name" -> Comparator.comparing(AdminCampusChargeMemberResult::name);
			case "email" -> Comparator.comparing(AdminCampusChargeMemberResult::email);
			case "totalAmount" -> Comparator.comparingInt(AdminCampusChargeMemberResult::totalAmount);
			case "unpaidAmount" -> Comparator.comparingInt(AdminCampusChargeMemberResult::unpaidAmount);
			case "paidAmount" -> Comparator.comparingInt(AdminCampusChargeMemberResult::paidAmount);
			case "waivedAmount" -> Comparator.comparingInt(AdminCampusChargeMemberResult::waivedAmount);
			case "canceledAmount" -> Comparator.comparingInt(AdminCampusChargeMemberResult::canceledAmount);
			default -> Comparator.comparing(
				AdminCampusChargeMemberResult::latestChargeCreatedAt,
				Comparator.nullsLast(Comparator.naturalOrder())
			);
		};
		if (order.isDescending()) {
			comparator = comparator.reversed();
		}
		return comparator.thenComparing(AdminCampusChargeMemberResult::userId);
	}

	private List<AdminCampusChargeMemberResult> page(List<AdminCampusChargeMemberResult> members, Pageable pageable) {
		int start = Math.toIntExact(Math.min(pageable.getOffset(), members.size()));
		int end = Math.min(start + pageable.getPageSize(), members.size());
		return members.subList(start, end);
	}

	private List<ChargeCategorySummaryResult> monthlyByCategory(List<ChargeItem> charges) {
		List<ChargeCategorySummaryResult> results = new ArrayList<>();
		for (PaymentCategory paymentCategory : PaymentCategory.values()) {
			List<ChargeItem> categoryCharges = charges.stream()
				.filter(charge -> charge.paymentCategory() == paymentCategory)
				.toList();
			if (categoryCharges.isEmpty()) {
				continue;
			}
			int paidAmount = categoryCharges.stream()
				.filter(charge -> charge.status() == ChargeStatus.PAID)
				.mapToInt(ChargeItem::amount)
				.sum();
			int unpaidAmount = categoryCharges.stream()
				.filter(charge -> charge.status() == ChargeStatus.UNPAID)
				.mapToInt(ChargeItem::amount)
				.sum();
			results.add(new ChargeCategorySummaryResult(
				paymentCategory,
				paidAmount,
				unpaidAmount,
				categoryCharges.stream().mapToInt(ChargeItem::amount).sum()
			));
		}
		return results;
	}

	private ChargeAmountSummaryResult summarize(List<ChargeItem> charges) {
		return new ChargeAmountSummaryResult(
			charges.stream().mapToInt(ChargeItem::amount).sum(),
			sumByStatus(charges, ChargeStatus.UNPAID),
			sumByStatus(charges, ChargeStatus.PAID),
			sumByStatus(charges, ChargeStatus.WAIVED),
			sumByStatus(charges, ChargeStatus.CANCELED)
		);
	}

	private int sumByStatus(List<ChargeItem> charges, ChargeStatus status) {
		return charges.stream()
			.filter(charge -> charge.status() == status)
			.mapToInt(ChargeItem::amount)
			.sum();
	}

	private boolean isInMonth(Instant instant, YearMonth yearMonth) {
		if (instant == null) {
			return false;
		}
		YearMonth target = YearMonth.from(instant.atZone(ZoneOffset.UTC));
		return target.equals(yearMonth);
	}

	private YearMonth yearMonth(int year, int month) {
		try {
			return YearMonth.of(year, month);
		} catch (DateTimeException exception) {
			throw new BusinessException(ErrorCode.BILLING_INVALID_YEAR_MONTH);
		}
	}

	private Campus getCampus(Long campusId) {
		return campusRepository.findById(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
	}

	private void requireCampusChargeManager(Long campusId, Long requesterId, PaymentCategory paymentCategory) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_CHARGE_LIST_FORBIDDEN, ADMIN_CHARGE_LIST_FORBIDDEN));
		if (paymentCategory == PaymentCategory.COFFEE && isActiveCoffeeDuty(campusId, requester.userId())) {
			return;
		}
		BillingAccessPolicy.requireCampusManager(
			requesterMembership,
			ErrorCode.BILLING_CHARGE_LIST_FORBIDDEN,
			ADMIN_CHARGE_LIST_FORBIDDEN
		);
	}

	private boolean isActiveCoffeeDuty(Long campusId, Long userId) {
		return dutyAssignmentRepository.findByCampusIdAndDutyTypeAndIsActiveTrue(campusId, DutyType.COFFEE)
			.map(assignment -> assignment.userId().equals(userId))
			.orElse(false);
	}

	private void requireActiveCampusMember(Long campusId, Long userId, String message) {
		campusMemberRepository.findByCampusIdAndUserId(campusId, userId)
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_CHARGE_LIST_FORBIDDEN, message));
	}

	private CampusUserLookupResult getActiveUser(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return user;
	}
}
