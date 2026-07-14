package com.faithlog.billing.service;

import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.policy.BillingAccessPolicy;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.service.result.PaymentAccountResult;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAccountQueryService {

	private static final String ACCOUNT_LIST_FORBIDDEN = "캠퍼스 납부 계좌 조회 권한이 없습니다.";

	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final PaymentAccountRepositoryPort paymentAccountRepository;

	public PaymentAccountQueryService(
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		PaymentAccountRepositoryPort paymentAccountRepository
	) {
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
		this.paymentAccountRepository = paymentAccountRepository;
	}

	@Transactional(readOnly = true)
	public List<PaymentAccountResult> listPaymentAccounts(Long campusId, Long requesterId) {
		requirePaymentAccountListAccess(campusId, requesterId);
		return paymentAccountRepository.findByCampusIdAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(campusId)
			.stream()
			.filter(account -> account.accountType() != PaymentCategory.MEAL)
			.map(PaymentAccountResult::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<PaymentAccountResult> listAdminPaymentAccounts(Long campusId, Long requesterId) {
		return listAdminPaymentAccounts(campusId, requesterId, null, false);
	}

	@Transactional(readOnly = true)
	public List<PaymentAccountResult> listAdminPaymentAccounts(
		Long campusId,
		Long requesterId,
		PaymentCategory accountType,
		boolean includeInactive
	) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin() || isCampusManager(campusId, requester.userId())) {
			return findAdminPaymentAccounts(campusId, accountType, includeInactive);
		}
		if (isActiveCoffeeDuty(campusId, requester.userId()) && (accountType == null || accountType == PaymentCategory.COFFEE)) {
			return paymentAccountRepository
				.findByCampusIdAndOwnerUserIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(
					campusId,
					requester.userId(),
					PaymentCategory.COFFEE
				)
				.stream()
				.map(PaymentAccountResult::from)
				.toList();
		}
		throw new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_LIST_FORBIDDEN, ACCOUNT_LIST_FORBIDDEN);
	}

	@Transactional(readOnly = true)
	public void requireActivePenaltyAccount(Long campusId) {
		paymentAccountRepository
			.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(campusId, PaymentCategory.PENALTY)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING));
	}

	private List<PaymentAccountResult> findAdminPaymentAccounts(
		Long campusId,
		PaymentCategory accountType,
		boolean includeInactive
	) {
		List<PaymentAccount> accounts;
		if (accountType != null && includeInactive) {
			accounts = paymentAccountRepository.findByCampusIdAndAccountTypeAndDeletedAtIsNullOrderByIdAsc(campusId, accountType);
		} else if (accountType != null) {
			accounts = paymentAccountRepository
				.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(campusId, accountType);
		} else if (includeInactive) {
			accounts = paymentAccountRepository.findByCampusIdAndDeletedAtIsNullOrderByIdAsc(campusId);
		} else {
			accounts = paymentAccountRepository.findByCampusIdAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(campusId);
		}
		return accounts.stream()
			.filter(account -> account.accountType() != PaymentCategory.MEAL)
			.map(PaymentAccountResult::from)
			.toList();
	}

	private boolean isCampusManager(Long campusId, Long requesterId) {
		return campusMemberRepository.findByCampusIdAndUserId(campusId, requesterId)
			.filter(CampusMember::isActive)
			.map(membership -> {
				try {
					BillingAccessPolicy.requirePaymentAccountManager(membership);
					return true;
				} catch (BusinessException exception) {
					return false;
				}
			})
			.orElse(false);
	}

	private boolean isActiveCoffeeDuty(Long campusId, Long userId) {
		return dutyAssignmentRepository
			.findByCampusIdAndDutyTypeAndUserIdAndIsActiveTrue(campusId, DutyType.COFFEE, userId)
			.isPresent();
	}

	private void requirePaymentAccountListAccess(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(
				ErrorCode.BILLING_PAYMENT_ACCOUNT_LIST_FORBIDDEN,
				ACCOUNT_LIST_FORBIDDEN
			));
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
