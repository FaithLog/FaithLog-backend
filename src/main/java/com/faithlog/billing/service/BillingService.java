package com.faithlog.billing.service;

import com.faithlog.billing.service.command.ChangeChargeStatusCommand;
import com.faithlog.billing.service.command.CompleteChargePaymentCommand;
import com.faithlog.billing.service.command.CreateCoffeeChargeCommand;
import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.billing.service.command.CreatePenaltyChargeCommand;
import com.faithlog.billing.service.result.ChargeItemResult;
import com.faithlog.billing.service.result.PaymentAccountResult;
import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.service.policy.BillingAccessPolicy;
import com.faithlog.billing.service.policy.ChargeStatusPolicy;
import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

	private static final String ACCOUNT_LIST_FORBIDDEN = "캠퍼스 납부 계좌 조회 권한이 없습니다.";

	private final PaymentAccountCommandService paymentAccountCommandService;
	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final ChargeItemRepositoryPort chargeItemRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;

	public BillingService(
		PaymentAccountCommandService paymentAccountCommandService,
		PaymentAccountRepositoryPort paymentAccountRepository,
		ChargeItemRepositoryPort chargeItemRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository
	) {
		this.paymentAccountCommandService = paymentAccountCommandService;
		this.paymentAccountRepository = paymentAccountRepository;
		this.chargeItemRepository = chargeItemRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
	}

	public PaymentAccountResult createPaymentAccount(CreatePaymentAccountCommand command) {
		return paymentAccountCommandService.createPaymentAccount(command);
	}

	public PaymentAccountResult deactivatePaymentAccount(Long accountId, Long requesterId) {
		return paymentAccountCommandService.deactivatePaymentAccount(accountId, requesterId);
	}

	public PaymentAccountResult activatePenaltyPaymentAccount(Long campusId, Long paymentAccountId, Long requesterId) {
		return paymentAccountCommandService.activatePenaltyPaymentAccount(campusId, paymentAccountId, requesterId);
	}

	public void deletePaymentAccount(Long campusId, Long paymentAccountId, Long requesterId) {
		paymentAccountCommandService.deletePaymentAccount(campusId, paymentAccountId, requesterId);
	}

	@Transactional(readOnly = true)
	public List<PaymentAccountResult> listPaymentAccounts(Long campusId, Long requesterId) {
		requirePaymentAccountListAccess(campusId, requesterId);
		return paymentAccountRepository.findByCampusIdAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(campusId)
			.stream()
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

	@Transactional
	public ChargeItemResult createPenaltyCharge(CreatePenaltyChargeCommand command) {
		PaymentAccount account = paymentAccountRepository
			.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(command.campusId(), PaymentCategory.PENALTY)
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING));

		ChargeItem existingCharge = chargeItemRepository
			.findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceId(
				command.campusId(),
				command.userId(),
				PaymentCategory.PENALTY,
				command.sourceType(),
				command.sourceId()
			)
			.orElse(null);
		if (existingCharge != null && existingCharge.isUnpaid()) {
			existingCharge.updateUnpaidCharge(
				account,
				command.title(),
				command.reason(),
				command.amount(),
				command.dueDate()
			);
			return ChargeItemResult.from(existingCharge);
		}
		if (existingCharge != null) {
			throw new BusinessException(ErrorCode.BILLING_TERMINAL_CHARGE_UPDATE_FORBIDDEN);
		}

		ChargeItem chargeItem = ChargeItem.create(
			command.campusId(),
			command.userId(),
			PaymentCategory.PENALTY,
			account.id(),
			account.bankName(),
			account.accountNumber(),
			account.accountHolder(),
			command.sourceType(),
			command.sourceId(),
			command.title(),
			command.reason(),
			command.amount(),
			command.dueDate()
		);
		return ChargeItemResult.from(chargeItemRepository.save(chargeItem));
	}

	@Transactional
	public ChargeItemResult createOrUpdateCoffeeCharge(CreateCoffeeChargeCommand command) {
		PaymentAccount account = findValidCoffeeAccount(command);
		ChargeItem existingCharge = chargeItemRepository
			.findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceId(
				command.campusId(),
				command.userId(),
				PaymentCategory.COFFEE,
				ChargeSourceType.POLL_RESPONSE,
				command.sourceId()
			)
			.orElse(null);
		if (existingCharge != null && existingCharge.isUnpaid()) {
			existingCharge.updateUnpaidCharge(
				account,
				command.title(),
				command.reason(),
				command.amount(),
				command.dueDate()
			);
			return ChargeItemResult.from(existingCharge);
		}
		if (existingCharge != null) {
			return ChargeItemResult.from(existingCharge);
		}

		ChargeItem chargeItem = ChargeItem.create(
			command.campusId(),
			command.userId(),
			PaymentCategory.COFFEE,
			account.id(),
			account.bankName(),
			account.accountNumber(),
			account.accountHolder(),
			ChargeSourceType.POLL_RESPONSE,
			command.sourceId(),
			command.title(),
			command.reason(),
			command.amount(),
			command.dueDate()
		);
		return ChargeItemResult.from(chargeItemRepository.save(chargeItem));
	}

	@Transactional
	public ChargeItemResult completeMyChargePayment(CompleteChargePaymentCommand command) {
		CampusUserLookupResult requester = getActiveUser(command.requesterId());
		ChargeItem chargeItem = chargeItemRepository.findChargeItemById(command.chargeItemId())
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
		ChargeItem chargeItem = chargeItemRepository.findChargeItemById(command.chargeItemId())
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_CHARGE_ITEM_NOT_FOUND));
		requireChargeStatusManager(chargeItem.campusId(), command.requesterId());
		ChargeStatusPolicy.applyAdminStatusChange(chargeItem, command.status());

		return ChargeItemResult.from(chargeItem);
	}

	private PaymentAccount findValidCoffeeAccount(CreateCoffeeChargeCommand command) {
		if (command.paymentAccountId() == null) {
			throw new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING);
		}
		PaymentAccount account = paymentAccountRepository.findById(command.paymentAccountId())
			.orElseThrow(() -> new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING));
		if (account.isDeleted() || !account.isActive() || !account.campusId().equals(command.campusId()) || account.accountType() != PaymentCategory.COFFEE) {
			throw new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING);
		}
		return account;
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
		return dutyAssignmentRepository.findByCampusIdAndDutyTypeAndIsActiveTrue(campusId, DutyType.COFFEE)
			.map(assignment -> assignment.userId().equals(userId))
			.orElse(false);
	}

	private void requirePaymentAccountListAccess(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		requireActiveCampusMember(campusId, requester.userId(), ErrorCode.BILLING_PAYMENT_ACCOUNT_LIST_FORBIDDEN, ACCOUNT_LIST_FORBIDDEN);
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

	private void requireActiveCampusMember(Long campusId, Long userId, ErrorCode errorCode) {
		requireActiveCampusMember(campusId, userId, errorCode, errorCode.message());
	}

	private void requireActiveCampusMember(Long campusId, Long userId, ErrorCode errorCode, String message) {
		campusMemberRepository.findByCampusIdAndUserId(campusId, userId)
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(errorCode, message));
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
