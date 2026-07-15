package com.faithlog.billing.service;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.command.ChangeChargeStatusCommand;
import com.faithlog.billing.service.command.CompleteChargePaymentCommand;
import com.faithlog.billing.service.policy.BillingAccessPolicy;
import com.faithlog.billing.service.policy.ChargeStatusPolicy;
import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.ChargeItemLockScope;
import com.faithlog.billing.service.port.DevotionChargeReopenPort;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.service.port.PaymentAccountLockScope;
import com.faithlog.billing.service.result.ChargeItemResult;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChargeStatusCommandService {

	private final ChargeItemRepositoryPort chargeItemRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;
	private final DevotionChargeReopenPort devotionChargeReopenPort;
	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final CampusRepositoryPort campusRepository;

	public ChargeStatusCommandService(
		ChargeItemRepositoryPort chargeItemRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort,
		DevotionChargeReopenPort devotionChargeReopenPort,
		PaymentAccountRepositoryPort paymentAccountRepository,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		CampusRepositoryPort campusRepository
	) {
		this.chargeItemRepository = chargeItemRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
		this.devotionChargeReopenPort = devotionChargeReopenPort;
		this.paymentAccountRepository = paymentAccountRepository;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
		this.campusRepository = campusRepository;
	}

	@Transactional
	public ChargeItemResult completeMyChargePayment(CompleteChargePaymentCommand command) {
		CampusUserLookupResult requester = getActiveUser(command.requesterId());
		ChargeItem chargeItem = chargeItemRepository.findChargeItemByIdForUpdate(command.chargeItemId())
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_CHARGE_ITEM_NOT_FOUND));

		if (!chargeItem.userId().equals(requester.userId())) {
			throw new BusinessException(ErrorCode.BILLING_MY_CHARGE_PAYMENT_FORBIDDEN);
		}
		if (!chargeItem.campusId().equals(command.campusId())) {
			throw new BusinessException(ErrorCode.BILLING_MY_CHARGE_CAMPUS_MISMATCH);
		}
		requireActiveCampusMember(command.campusId(), requester.userId(), ErrorCode.BILLING_MY_CHARGE_PAYMENT_FORBIDDEN);
		if (!chargeItem.isUnpaid()) {
			throw new BusinessException(ErrorCode.BILLING_MY_CHARGE_PAYMENT_CONFLICT);
		}

		chargeItem.markPaid(command.paidAt());
		return ChargeItemResult.from(chargeItem);
	}

	@Transactional
	public ChargeItemResult changeChargeStatus(ChangeChargeStatusCommand command) {
		ChargeItemLockScope chargeScope = chargeItemRepository.findChargeItemLockScopeById(command.chargeItemId())
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_CHARGE_ITEM_NOT_FOUND));
		CampusUserLookupResult requester = isStaleDutyRecoveryCandidate(chargeScope)
			? getActiveUserForUpdate(command.requesterId())
			: getActiveUser(command.requesterId());
		boolean staleDutyRecovery = requireStaleDutyRecoveryScopeIfEligible(chargeScope, requester);
		if (staleDutyRecovery && command.status() == ChargeStatus.UNPAID) {
			throw new BusinessException(ErrorCode.BILLING_CHARGE_STATUS_TRANSITION_CONFLICT);
		}
		if (chargeScope.getPaymentCategory() == PaymentCategory.MEAL && !staleDutyRecovery) {
			throw new BusinessException(ErrorCode.BILLING_CHARGE_ITEM_NOT_FOUND);
		}
		if (chargeScope.getPaymentCategory() == PaymentCategory.COFFEE && !staleDutyRecovery) {
			requireOwnedCoffeeChargeManagerForUpdate(chargeScope, command.requesterId());
		} else if (chargeScope.getPaymentCategory() != PaymentCategory.MEAL && !staleDutyRecovery) {
			requireChargeStatusManager(chargeScope.getCampusId(), command.requesterId());
		}
		ChargeItem chargeItem = chargeItemRepository.findChargeItemByIdForUpdate(command.chargeItemId())
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_CHARGE_ITEM_NOT_FOUND));
		requireLockedChargeWithinAuthorizedScope(
			chargeScope, chargeItem, command.requesterId(), staleDutyRecovery);
		ChargeStatusPolicy.applyAdminStatusChange(chargeItem, command.status());
		if (shouldReopenWeeklyDevotion(chargeItem, command.status())) {
			devotionChargeReopenPort.reopenWeeklyDevotion(
				chargeItem.campusId(),
				chargeItem.userId(),
				chargeItem.sourceId()
			);
		}

		return ChargeItemResult.from(chargeItem);
	}

	private boolean requireStaleDutyRecoveryScopeIfEligible(
		ChargeItemLockScope chargeScope,
		CampusUserLookupResult requester
	) {
		if (!isStaleDutyRecoveryCandidate(chargeScope) || !requester.isAdmin()) {
			return false;
		}
		PaymentAccountLockScope account = paymentAccountRepository
			.findLockScopeById(chargeScope.getPaymentAccountId())
			.filter(candidate -> candidate.campusId().equals(chargeScope.getCampusId()))
			.filter(candidate -> candidate.accountType() == chargeScope.getPaymentCategory())
			.filter(candidate -> candidate.ownerUserId() != null)
			.orElse(null);
		if (account == null) {
			return false;
		}
		campusRepository.findByIdForUpdate(chargeScope.getCampusId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
		DutyType dutyType = chargeScope.getPaymentCategory() == PaymentCategory.COFFEE
			? DutyType.COFFEE
			: DutyType.MEAL;
		if (dutyAssignmentRepository.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
			chargeScope.getCampusId(), dutyType, account.ownerUserId()).isEmpty()) {
			return false;
		}
		return campusMemberRepository.findByCampusIdAndUserIdForUpdate(
			chargeScope.getCampusId(), account.ownerUserId())
			.filter(member -> !member.isActive())
			.isPresent();
	}

	private boolean isStaleDutyRecoveryCandidate(ChargeItemLockScope chargeScope) {
		return chargeScope.getStatus() == ChargeStatus.UNPAID
			&& (chargeScope.getPaymentCategory() == PaymentCategory.COFFEE
				|| chargeScope.getPaymentCategory() == PaymentCategory.MEAL)
			&& chargeScope.getPaymentAccountId() != null;
	}

	private boolean shouldReopenWeeklyDevotion(ChargeItem chargeItem, ChargeStatus targetStatus) {
		return targetStatus == ChargeStatus.CANCELED
			&& chargeItem.paymentCategory() == PaymentCategory.PENALTY
			&& chargeItem.sourceType() == ChargeSourceType.DEVOTION_RECORD;
	}

	private void requireChargeStatusManager(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_CHARGE_STATUS_MANAGE_FORBIDDEN));
		BillingAccessPolicy.requireChargeStatusManager(requesterMembership);
	}

	private void requireOwnedCoffeeChargeManagerForUpdate(ChargeItemLockScope chargeItem, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		requireActiveCampusMember(
			chargeItem.getCampusId(), requester.userId(), ErrorCode.BILLING_CHARGE_STATUS_MANAGE_FORBIDDEN);
		if (!requireActiveCoffeeDutyForUpdate(
			chargeItem.getCampusId(), requester.userId())) {
			throw new BusinessException(ErrorCode.BILLING_CHARGE_STATUS_MANAGE_FORBIDDEN);
		}
		requireOwnedCoffeeAccount(
			chargeItem.getCampusId(), chargeItem.getPaymentAccountId(), requester.userId());
	}

	private void requireLockedChargeWithinAuthorizedScope(
		ChargeItemLockScope authorizedScope,
		ChargeItem lockedCharge,
		Long requesterId,
		boolean staleDutyRecovery
	) {
		if (!lockedCharge.campusId().equals(authorizedScope.getCampusId())
			|| lockedCharge.paymentCategory() != authorizedScope.getPaymentCategory()
			|| !java.util.Objects.equals(
				lockedCharge.paymentAccountId(), authorizedScope.getPaymentAccountId())) {
			throw new BusinessException(ErrorCode.BILLING_CHARGE_STATUS_MANAGE_FORBIDDEN);
		}
		if (staleDutyRecovery && !lockedCharge.isUnpaid()) {
			throw new BusinessException(ErrorCode.BILLING_CHARGE_STATUS_TRANSITION_CONFLICT);
		}
		if (!staleDutyRecovery && lockedCharge.paymentCategory() == PaymentCategory.COFFEE) {
			requireOwnedCoffeeAccount(
				lockedCharge.campusId(), lockedCharge.paymentAccountId(), requesterId);
		}
	}

	private void requireOwnedCoffeeAccount(Long campusId, Long paymentAccountId, Long requesterId) {
		if (paymentAccountId == null) {
			throw new BusinessException(ErrorCode.BILLING_CHARGE_STATUS_MANAGE_FORBIDDEN);
		}
		boolean ownsAccount = paymentAccountRepository.findById(paymentAccountId)
			.filter(account -> account.campusId().equals(campusId))
			.filter(account -> account.accountType() == PaymentCategory.COFFEE)
			.filter(account -> requesterId.equals(account.ownerUserId()))
			.isPresent();
		if (!ownsAccount) {
			throw new BusinessException(ErrorCode.BILLING_CHARGE_STATUS_MANAGE_FORBIDDEN);
		}
	}

	private boolean requireActiveCoffeeDutyForUpdate(Long campusId, Long requesterId) {
		return dutyAssignmentRepository.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
			campusId, DutyType.COFFEE, requesterId).isPresent();
	}

	private void requireActiveCampusMember(Long campusId, Long userId, ErrorCode errorCode) {
		campusMemberRepository.findByCampusIdAndUserId(campusId, userId)
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(errorCode));
	}

	private CampusUserLookupResult getActiveUser(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return user;
	}

	private CampusUserLookupResult getActiveUserForUpdate(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserByIdForUpdate(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return user;
	}
}
