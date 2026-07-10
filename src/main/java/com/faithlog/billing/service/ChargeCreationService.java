package com.faithlog.billing.service;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.command.CreateCoffeeChargeCommand;
import com.faithlog.billing.service.command.CreatePenaltyChargeCommand;
import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.service.result.ChargeItemResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChargeCreationService {

	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final ChargeItemRepositoryPort chargeItemRepository;

	public ChargeCreationService(
		PaymentAccountRepositoryPort paymentAccountRepository,
		ChargeItemRepositoryPort chargeItemRepository
	) {
		this.paymentAccountRepository = paymentAccountRepository;
		this.chargeItemRepository = chargeItemRepository;
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
}
