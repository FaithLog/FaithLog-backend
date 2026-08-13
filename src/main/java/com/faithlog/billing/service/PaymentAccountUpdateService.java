package com.faithlog.billing.service;

import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.service.command.UpdatePaymentAccountCommand;
import com.faithlog.billing.service.policy.BillingAccessPolicy;
import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.PaymentAccountLockScope;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.service.result.PaymentAccountResult;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.service.MealDutyAccessService;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAccountUpdateService {

	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final ChargeItemRepositoryPort chargeItemRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final MealDutyAccessService mealDutyAccessService;

	public PaymentAccountUpdateService(
		PaymentAccountRepositoryPort paymentAccountRepository,
		ChargeItemRepositoryPort chargeItemRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		MealDutyAccessService mealDutyAccessService
	) {
		this.paymentAccountRepository = paymentAccountRepository;
		this.chargeItemRepository = chargeItemRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
		this.mealDutyAccessService = mealDutyAccessService;
	}

	@Transactional
	public PaymentAccountResult updatePaymentAccount(UpdatePaymentAccountCommand command) {
		PaymentAccountLockScope scope = paymentAccountRepository.findLockScopeById(command.accountId())
			.filter(candidate -> candidate.deletedAt() == null)
			.filter(candidate -> candidate.campusId().equals(command.campusId()))
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_NOT_FOUND));
		requireUpdateAccess(scope, command.requesterId());

		PaymentAccount account = paymentAccountRepository.findByIdForUpdate(command.accountId())
			.filter(candidate -> !candidate.isDeleted())
			.filter(candidate -> candidate.campusId().equals(scope.campusId()))
			.filter(candidate -> candidate.accountType() == scope.accountType())
			.filter(candidate -> Objects.equals(candidate.ownerUserId(), scope.ownerUserId()))
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_NOT_FOUND));

		account.updateDetails(
			command.nickname(),
			command.bankName(),
			command.accountNumber(),
			command.accountHolder()
		);
		refreshLinkedUnpaidChargeSnapshots(account);
		return PaymentAccountResult.from(account);
	}

	private void requireUpdateAccess(PaymentAccountLockScope scope, Long requesterId) {
		switch (scope.accountType()) {
			case PENALTY -> requirePenaltyManager(scope.campusId(), requesterId);
			case COFFEE -> requireCoffeeOwnerDuty(scope, requesterId);
			case MEAL -> requireMealOwnerDuty(scope, requesterId);
		}
	}

	private void requirePenaltyManager(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember membership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_MANAGE_FORBIDDEN));
		BillingAccessPolicy.requirePaymentAccountManager(membership);
	}

	private void requireCoffeeOwnerDuty(PaymentAccountLockScope scope, Long requesterId) {
		getActiveUser(requesterId);
		if (!requesterId.equals(scope.ownerUserId())) {
			throw new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_OWNER_FORBIDDEN);
		}
		campusMemberRepository.findByCampusIdAndUserId(scope.campusId(), requesterId)
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_MANAGE_FORBIDDEN));
		dutyAssignmentRepository.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
			scope.campusId(), DutyType.COFFEE, requesterId
		).orElseThrow(() -> new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_MANAGE_FORBIDDEN));
	}

	private void requireMealOwnerDuty(PaymentAccountLockScope scope, Long requesterId) {
		mealDutyAccessService.requireActiveMealDutyForUpdate(scope.campusId(), requesterId);
		if (!requesterId.equals(scope.ownerUserId())) {
			throw new BusinessException(ErrorCode.MEAL_PAYMENT_ACCOUNT_NOT_FOUND);
		}
	}

	private CampusUserLookupResult getActiveUser(Long requesterId) {
		return userLookupPort.findCampusUserById(requesterId)
			.filter(CampusUserLookupResult::active)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
	}

	private void refreshLinkedUnpaidChargeSnapshots(PaymentAccount account) {
		chargeItemRepository
			.findByCampusIdAndPaymentCategoryAndStatusAndPaymentAccountIdInOrderByIdAscForUpdate(
				account.campusId(),
				account.accountType(),
				ChargeStatus.UNPAID,
				Set.of(account.id())
			)
			.forEach(charge -> charge.reconnectPaymentAccount(account));
	}
}
