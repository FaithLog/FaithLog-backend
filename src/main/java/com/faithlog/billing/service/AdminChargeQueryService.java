package com.faithlog.billing.service;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.policy.BillingAccessPolicy;
import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.service.query.AdminCampusChargeListQuery;
import com.faithlog.billing.service.query.AdminMemberChargeListQuery;
import com.faithlog.billing.service.query.ChargeSearchCriteria;
import com.faithlog.billing.service.result.AdminCampusChargeMemberResult;
import com.faithlog.billing.service.result.AdminCampusChargesResult;
import com.faithlog.billing.service.result.AdminMemberChargesResult;
import com.faithlog.billing.service.result.ChargeAmountSummaryResult;
import com.faithlog.billing.service.result.ChargeListItemResult;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.MealDutyAccessService;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.time.Instant;
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
public class AdminChargeQueryService {

	private static final String ADMIN_CHARGE_LIST_FORBIDDEN = "캠퍼스 청구 조회 권한이 없습니다.";

	private final ChargeItemRepositoryPort chargeItemRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final MealDutyAccessService mealDutyAccessService;

	public AdminChargeQueryService(
		ChargeItemRepositoryPort chargeItemRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		PaymentAccountRepositoryPort paymentAccountRepository,
		MealDutyAccessService mealDutyAccessService
	) {
		this.chargeItemRepository = chargeItemRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
		this.paymentAccountRepository = paymentAccountRepository;
		this.mealDutyAccessService = mealDutyAccessService;
	}

	@Transactional(readOnly = true)
	public AdminCampusChargesResult listAdminCampusCharges(AdminCampusChargeListQuery query) {
		if (query.paymentCategory() == PaymentCategory.MEAL) {
			throw forbidden();
		}
		Campus campus = getCampus(query.campusId());
		Set<Long> paymentAccountIds = resolveChargePaymentAccountIds(
			query.campusId(),
			query.requesterId(),
			query.paymentCategory(),
			query.paymentAccountId()
		);
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
			query.status(),
			paymentAccountIds,
			PaymentCategory.MEAL
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
	public AdminCampusChargesResult listAdminCampusChargesForMyAccounts(AdminCampusChargeListQuery query) {
		if (query.paymentCategory() == PaymentCategory.MEAL) {
			throw forbidden();
		}
		Campus campus = getCampus(query.campusId());
		Set<Long> paymentAccountIds = resolveMyChargePaymentAccountIds(
			query.campusId(),
			query.requesterId(),
			query.paymentCategory(),
			query.paymentAccountId()
		);
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
			query.status(),
			paymentAccountIds,
			PaymentCategory.MEAL
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
		if (query.paymentCategory() == PaymentCategory.MEAL) {
			throw forbidden();
		}
		Campus campus = getCampus(query.campusId());
		requireCampusChargeManager(query.campusId(), query.requesterId(), query.paymentCategory());
		requireActiveCampusMember(query.campusId(), query.userId());
		CampusUserLookupResult targetUser = getActiveUser(query.userId());
		Set<Long> paymentAccountIds = resolveMemberChargePaymentAccountIds(
			query.campusId(), query.requesterId(), query.paymentCategory());

		ChargeSearchCriteria criteria = new ChargeSearchCriteria(
			query.campusId(),
			Set.of(query.userId()),
			query.paymentCategory(),
			query.status(),
			paymentAccountIds,
			PaymentCategory.MEAL
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

	@Transactional(readOnly = true)
	public AdminCampusChargesResult listMealChargesForMyAccounts(AdminCampusChargeListQuery query) {
		mealDutyAccessService.requireActiveMealDuty(query.campusId(), query.requesterId());
		Campus campus = getCampus(query.campusId());
		Set<Long> accountIds = paymentAccountRepository
			.findByCampusIdAndOwnerUserIdAndAccountTypeAndDeletedAtIsNullOrderByIdAsc(
				query.campusId(), query.requesterId(), PaymentCategory.MEAL)
			.stream().map(PaymentAccount::id).collect(Collectors.toSet());
		List<CampusUserLookupResult> targetUsers = targetUsers(query.campusId(), query.userId(), query.keyword());
		Set<Long> targetUserIds = targetUsers.stream().map(CampusUserLookupResult::userId).collect(Collectors.toSet());
		Map<Long, CampusUserLookupResult> usersById = targetUsers.stream()
			.collect(Collectors.toMap(CampusUserLookupResult::userId, Function.identity()));
		List<ChargeItem> charges = chargeItemRepository.searchCharges(new ChargeSearchCriteria(
			query.campusId(), targetUserIds, PaymentCategory.MEAL, query.status(), accountIds
		));
		return new AdminCampusChargesResult(
			campus.id(), campus.name(), campus.region(), summarize(charges),
			aggregateMembers(charges, usersById, query.pageable())
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

	private Campus getCampus(Long campusId) {
		return campusRepository.findById(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
	}

	private Set<Long> resolveChargePaymentAccountIds(
		Long campusId,
		Long requesterId,
		PaymentCategory paymentCategory,
		Long paymentAccountId
	) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin() || isCampusChargeManager(campusId, requester.userId())) {
			if (paymentAccountId == null) {
				return null;
			}
			PaymentAccount account = getAccountInCampus(campusId, paymentAccountId);
			return Set.of(account.id());
		}
		if (!isActiveCoffeeDuty(campusId, requester.userId())) {
			throw forbidden();
		}
		if (paymentCategory != null && paymentCategory != PaymentCategory.COFFEE) {
			throw forbidden();
		}
		if (paymentAccountId != null) {
			PaymentAccount account = getAccountInCampus(campusId, paymentAccountId);
			requireUsableCoffeeAccount(account, requester.userId());
			return Set.of(account.id());
		}
		return paymentAccountRepository
			.findByCampusIdAndOwnerUserIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(
				campusId,
				requester.userId(),
				PaymentCategory.COFFEE
			)
			.stream()
			.map(PaymentAccount::id)
			.collect(Collectors.toSet());
	}

	private Set<Long> resolveMyChargePaymentAccountIds(
		Long campusId,
		Long requesterId,
		PaymentCategory paymentCategory,
		Long paymentAccountId
	) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin() || isCampusChargeManager(campusId, requester.userId())) {
			return filterAccountIds(
				managerMyAccountCandidates(campusId, requester.userId(), paymentCategory),
				paymentCategory,
				paymentAccountId
			);
		}
		if (isActiveCoffeeDuty(campusId, requester.userId())) {
			if (paymentCategory != null && paymentCategory != PaymentCategory.COFFEE) {
				throw forbidden();
			}
			return filterAccountIds(
				paymentAccountRepository
					.findByCampusIdAndOwnerUserIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(
						campusId,
						requester.userId(),
						PaymentCategory.COFFEE
					),
				paymentCategory,
				paymentAccountId
			);
		}
		throw forbidden();
	}

	private Set<Long> resolveMemberChargePaymentAccountIds(
		Long campusId,
		Long requesterId,
		PaymentCategory paymentCategory
	) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin() || isCampusChargeManager(campusId, requester.userId())) {
			return null;
		}
		if (paymentCategory != PaymentCategory.COFFEE || !isActiveCoffeeDuty(campusId, requester.userId())) {
			throw forbidden();
		}
		return paymentAccountRepository.findByCampusIdAndOwnerUserIdAndAccountTypeOrderByIdAsc(
			campusId, requester.userId(), PaymentCategory.COFFEE)
			.stream()
			.map(PaymentAccount::id)
			.collect(Collectors.toSet());
	}

	private List<PaymentAccount> managerMyAccountCandidates(
		Long campusId,
		Long requesterId,
		PaymentCategory paymentCategory
	) {
		List<PaymentAccount> accounts = new ArrayList<>();
		if (paymentCategory == null || paymentCategory == PaymentCategory.PENALTY) {
			accounts.addAll(paymentAccountRepository
				.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(
					campusId,
					PaymentCategory.PENALTY
				));
		}
		if (paymentCategory == null || paymentCategory == PaymentCategory.COFFEE) {
			accounts.addAll(paymentAccountRepository
				.findByCampusIdAndOwnerUserIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(
					campusId,
					requesterId,
					PaymentCategory.COFFEE
				));
		}
		return accounts;
	}

	private Set<Long> filterAccountIds(
		List<PaymentAccount> accounts,
		PaymentCategory paymentCategory,
		Long paymentAccountId
	) {
		return accounts.stream()
			.filter(account -> paymentCategory == null || account.accountType() == paymentCategory)
			.filter(account -> paymentAccountId == null || account.id().equals(paymentAccountId))
			.map(PaymentAccount::id)
			.collect(Collectors.toSet());
	}

	private PaymentAccount getAccountInCampus(Long campusId, Long paymentAccountId) {
		PaymentAccount account = paymentAccountRepository.findById(paymentAccountId)
			.filter(paymentAccount -> !paymentAccount.isDeleted())
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_NOT_FOUND));
		if (!account.campusId().equals(campusId)) {
			throw new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_NOT_FOUND);
		}
		if (account.accountType() == PaymentCategory.MEAL) {
			throw forbidden();
		}
		return account;
	}

	private void requireUsableCoffeeAccount(PaymentAccount account, Long requesterId) {
		if (!account.isActive() || account.accountType() != PaymentCategory.COFFEE) {
			throw forbidden();
		}
		if (!requesterId.equals(account.ownerUserId())) {
			throw forbidden();
		}
	}

	private boolean isCampusChargeManager(Long campusId, Long requesterId) {
		return campusMemberRepository.findByCampusIdAndUserId(campusId, requesterId)
			.filter(CampusMember::isActive)
			.map(membership -> {
				try {
					BillingAccessPolicy.requireCampusManager(
						membership,
						ErrorCode.BILLING_CHARGE_LIST_FORBIDDEN,
						ADMIN_CHARGE_LIST_FORBIDDEN
					);
					return true;
				} catch (BusinessException exception) {
					return false;
				}
			})
			.orElse(false);
	}

	private void requireCampusChargeManager(Long campusId, Long requesterId, PaymentCategory paymentCategory) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(this::forbidden);
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
		return dutyAssignmentRepository
			.findByCampusIdAndDutyTypeAndUserIdAndIsActiveTrue(campusId, DutyType.COFFEE, userId)
			.isPresent();
	}

	private void requireActiveCampusMember(Long campusId, Long userId) {
		campusMemberRepository.findByCampusIdAndUserId(campusId, userId)
			.filter(CampusMember::isActive)
			.orElseThrow(this::forbidden);
	}

	private CampusUserLookupResult getActiveUser(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return user;
	}

	private BusinessException forbidden() {
		return new BusinessException(ErrorCode.BILLING_CHARGE_LIST_FORBIDDEN, ADMIN_CHARGE_LIST_FORBIDDEN);
	}
}
