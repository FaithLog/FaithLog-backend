package com.faithlog.billing.service;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.policy.ChargeArchivePolicy;
import com.faithlog.billing.service.query.ChargeSearchCriteria;
import com.faithlog.billing.service.query.MyChargeListQuery;
import com.faithlog.billing.service.query.MyChargeSummaryQuery;
import com.faithlog.billing.service.result.ChargeAmountSummaryResult;
import com.faithlog.billing.service.result.ChargeCategorySummaryResult;
import com.faithlog.billing.service.result.ChargeListItemResult;
import com.faithlog.billing.service.result.MyChargeSummaryResult;
import com.faithlog.billing.service.result.MyChargesResult;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.time.DateTimeException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyChargeQueryService {

	private static final String MY_CHARGE_LIST_FORBIDDEN = "본인 청구 조회 권한이 없습니다.";

	private final ChargeItemRepositoryPort chargeItemRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;
	private final Clock clock;

	public MyChargeQueryService(
		ChargeItemRepositoryPort chargeItemRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort,
		Clock clock
	) {
		this.chargeItemRepository = chargeItemRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public MyChargesResult listMyCharges(MyChargeListQuery query) {
		Campus campus = getCampus(query.campusId());
		CampusUserLookupResult requester = getActiveUser(query.requesterId());
		requireActiveCampusMember(query.campusId(), requester.userId());

		Set<Long> userIds = Set.of(requester.userId());
		ChargeSearchCriteria criteria = new ChargeSearchCriteria(
			query.campusId(),
			userIds,
			query.paymentCategory(),
			query.status(),
			null,
			null,
			ChargeArchivePolicy.terminalCompletedAtFrom(clock, query.includeArchived())
		);
		List<ChargeItem> summaryTargets = chargeItemRepository.searchCharges(criteria);
		Page<ChargeItem> page = chargeItemRepository.searchCharges(criteria, query.pageable());
		return new MyChargesResult(
			campus.id(),
			campus.name(),
			campus.region(),
			summarize(summaryTargets),
			page.stream().map(ChargeListItemResult::from).toList(),
			page.getNumber(),
			page.getSize(),
			page.getTotalElements(),
			page.getTotalPages()
		);
	}

	@Transactional(readOnly = true)
	public MyChargeSummaryResult getMyChargeSummary(MyChargeSummaryQuery query) {
		Campus campus = getCampus(query.campusId());
		CampusUserLookupResult requester = getActiveUser(query.requesterId());
		requireActiveCampusMember(query.campusId(), requester.userId());
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

	private void requireActiveCampusMember(Long campusId, Long userId) {
		campusMemberRepository.findByCampusIdAndUserId(campusId, userId)
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_CHARGE_LIST_FORBIDDEN, MY_CHARGE_LIST_FORBIDDEN));
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
