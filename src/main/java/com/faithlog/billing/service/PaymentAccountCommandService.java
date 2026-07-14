package com.faithlog.billing.service;

import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.billing.service.policy.BillingAccessPolicy;
import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.service.result.PaymentAccountResult;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAccountCommandService {

	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final ChargeItemRepositoryPort chargeItemRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;

	public PaymentAccountCommandService(
		PaymentAccountRepositoryPort paymentAccountRepository,
		ChargeItemRepositoryPort chargeItemRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository
	) {
		this.paymentAccountRepository = paymentAccountRepository;
		this.chargeItemRepository = chargeItemRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
	}

	@Transactional
	public PaymentAccountResult createPaymentAccount(CreatePaymentAccountCommand command) {
		if (command.accountType() == PaymentCategory.MEAL) {
			throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED, "MEAL 계좌는 밥 계좌 API에서만 등록할 수 있습니다.");
		}
		requirePaymentAccountManager(command.campusId(), command.requesterId(), command.accountType());
		lockCampusOrThrow(command.campusId());
		Long ownerUserId = resolveOwnerUserId(command);

		if (deactivatePreviousActiveAccount(command.campusId(), command.accountType(), ownerUserId)) {
			paymentAccountRepository.flush();
		}

		PaymentAccount account = paymentAccountRepository.save(PaymentAccount.create(
			command.campusId(),
			command.accountType(),
			command.nickname(),
			command.bankName(),
			command.accountNumber(),
			command.accountHolder(),
			ownerUserId
		));

		if (account.accountType() == PaymentCategory.PENALTY) {
			reconnectUnpaidCharges(account);
		}
		return PaymentAccountResult.from(account);
	}

	@Transactional
	public PaymentAccountResult deactivatePaymentAccount(Long accountId, Long requesterId) {
		PaymentAccount account = paymentAccountRepository.findById(accountId)
			.filter(paymentAccount -> !paymentAccount.isDeleted())
			.filter(paymentAccount -> paymentAccount.accountType() != PaymentCategory.MEAL)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_NOT_FOUND));
		requireCoffeeAccountOwnerIfNeeded(account, requesterId);
		requirePaymentAccountManager(account.campusId(), requesterId, account.accountType());
		account = lockCoffeeAccountIfNeeded(account);

		account.deactivate();
		return PaymentAccountResult.from(account);
	}

	@Transactional
	public PaymentAccountResult activatePenaltyPaymentAccount(Long campusId, Long paymentAccountId, Long requesterId) {
		lockCampusOrThrow(campusId);
		PaymentAccount account = findPaymentAccountInCampus(campusId, paymentAccountId);
		if (account.accountType() != PaymentCategory.PENALTY) {
			throw new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_ACTIVATE_UNSUPPORTED);
		}
		requirePaymentAccountManager(campusId, requesterId, PaymentCategory.PENALTY);
		if (account.isActive()) {
			return PaymentAccountResult.from(account);
		}

		paymentAccountRepository
			.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(campusId, PaymentCategory.PENALTY)
			.filter(activeAccount -> !activeAccount.id().equals(account.id()))
			.ifPresent(activeAccount -> {
				activeAccount.deactivate();
				paymentAccountRepository.flush();
			});
		account.activate();
		reconnectUnpaidCharges(account);
		return PaymentAccountResult.from(account);
	}

	@Transactional
	public void deletePaymentAccount(Long campusId, Long paymentAccountId, Long requesterId) {
		PaymentAccount account = findPaymentAccountInCampus(campusId, paymentAccountId);
		requireCoffeeAccountOwnerIfNeeded(account, requesterId);
		requirePaymentAccountManager(campusId, requesterId, account.accountType());
		account = lockCoffeeAccountIfNeeded(account);
		if (account.isActive()) {
			throw new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_ACTIVE_DELETE_FORBIDDEN);
		}
		account.softDelete();
	}

	private void reconnectUnpaidCharges(PaymentAccount account) {
		chargeItemRepository.findByCampusIdAndPaymentCategoryAndStatus(
				account.campusId(),
				account.accountType(),
				ChargeStatus.UNPAID
			)
			.forEach(chargeItem -> chargeItem.reconnectPaymentAccount(account));
	}

	private Long resolveOwnerUserId(CreatePaymentAccountCommand command) {
		if (command.accountType() == PaymentCategory.PENALTY) {
			return command.ownerUserId() == null ? command.requesterId() : command.ownerUserId();
		}
		if (command.ownerUserId() != null && !command.ownerUserId().equals(command.requesterId())) {
			throw new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_OWNER_FORBIDDEN);
		}
		return command.requesterId();
	}

	private boolean deactivatePreviousActiveAccount(Long campusId, PaymentCategory accountType, Long ownerUserId) {
		if (accountType == PaymentCategory.COFFEE) {
			return paymentAccountRepository
				.findByCampusIdAndAccountTypeAndOwnerUserIdAndIsActiveTrueAndDeletedAtIsNull(campusId, accountType, ownerUserId)
				.map(activeAccount -> {
					activeAccount.deactivate();
					return true;
				})
				.orElse(false);
		}
		return paymentAccountRepository
			.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(campusId, accountType)
			.map(activeAccount -> {
				activeAccount.deactivate();
				return true;
			})
			.orElse(false);
	}

	private void requireCoffeeAccountOwnerIfNeeded(PaymentAccount account, Long requesterId) {
		if (account.accountType() != PaymentCategory.COFFEE) {
			return;
		}
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (!requester.userId().equals(account.ownerUserId())) {
			throw new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_OWNER_FORBIDDEN);
		}
	}

	private PaymentAccount findPaymentAccountInCampus(Long campusId, Long paymentAccountId) {
		return paymentAccountRepository.findById(paymentAccountId)
			.filter(account -> !account.isDeleted())
			.filter(account -> account.accountType() != PaymentCategory.MEAL)
			.filter(account -> account.campusId().equals(campusId))
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_NOT_FOUND));
	}

	private void requirePaymentAccountManager(Long campusId, Long requesterId, PaymentCategory accountType) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (accountType == PaymentCategory.COFFEE) {
			campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
				.filter(CampusMember::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_MANAGE_FORBIDDEN));
			if (!requireActiveCoffeeDutyForUpdate(campusId, requester.userId())) {
				throw new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_MANAGE_FORBIDDEN);
			}
			return;
		}
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_MANAGE_FORBIDDEN));
		BillingAccessPolicy.requirePaymentAccountManager(requesterMembership);
	}

	private boolean requireActiveCoffeeDutyForUpdate(Long campusId, Long userId) {
		return dutyAssignmentRepository
			.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(campusId, DutyType.COFFEE, userId)
			.isPresent();
	}

	private PaymentAccount lockCoffeeAccountIfNeeded(PaymentAccount account) {
		if (account.accountType() != PaymentCategory.COFFEE) {
			return account;
		}
		return paymentAccountRepository.findByIdForUpdate(account.id())
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_PAYMENT_ACCOUNT_NOT_FOUND));
	}

	private CampusUserLookupResult getActiveUser(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return user;
	}

	private void lockCampusOrThrow(Long campusId) {
		campusRepository.findByIdForUpdate(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
	}
}
