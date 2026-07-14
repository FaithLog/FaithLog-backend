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
import com.faithlog.billing.service.port.DevotionChargeReopenPort;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.service.result.ChargeItemResult;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
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

	public ChargeStatusCommandService(
		ChargeItemRepositoryPort chargeItemRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort,
		DevotionChargeReopenPort devotionChargeReopenPort,
		PaymentAccountRepositoryPort paymentAccountRepository,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository
	) {
		this.chargeItemRepository = chargeItemRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
		this.devotionChargeReopenPort = devotionChargeReopenPort;
		this.paymentAccountRepository = paymentAccountRepository;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
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
		ChargeItem chargeItem = chargeItemRepository.findChargeItemByIdForUpdate(command.chargeItemId())
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_CHARGE_ITEM_NOT_FOUND));
		if (chargeItem.paymentCategory() == PaymentCategory.MEAL) {
			throw new BusinessException(ErrorCode.BILLING_CHARGE_ITEM_NOT_FOUND);
		}
		if (chargeItem.paymentCategory() == PaymentCategory.COFFEE) {
			requireOwnedCoffeeChargeManager(chargeItem, command.requesterId());
		} else {
			requireChargeStatusManager(chargeItem.campusId(), command.requesterId());
		}
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

	private void requireOwnedCoffeeChargeManager(ChargeItem chargeItem, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		requireActiveCampusMember(
			chargeItem.campusId(), requester.userId(), ErrorCode.BILLING_CHARGE_STATUS_MANAGE_FORBIDDEN);
		if (dutyAssignmentRepository.findByCampusIdAndDutyTypeAndUserIdAndIsActiveTrue(
			chargeItem.campusId(), DutyType.COFFEE, requester.userId()).isEmpty()) {
			throw new BusinessException(ErrorCode.BILLING_CHARGE_STATUS_MANAGE_FORBIDDEN);
		}
		boolean ownsAccount = paymentAccountRepository.findById(chargeItem.paymentAccountId())
			.filter(account -> account.campusId().equals(chargeItem.campusId()))
			.filter(account -> account.accountType() == PaymentCategory.COFFEE)
			.filter(account -> requester.userId().equals(account.ownerUserId()))
			.isPresent();
		if (!ownsAccount) {
			throw new BusinessException(ErrorCode.BILLING_CHARGE_STATUS_MANAGE_FORBIDDEN);
		}
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
}
