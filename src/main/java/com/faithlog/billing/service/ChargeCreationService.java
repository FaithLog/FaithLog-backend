package com.faithlog.billing.service;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.command.CreateCoffeeChargeCommand;
import com.faithlog.billing.service.command.CreatePenaltyChargeCommand;
import com.faithlog.billing.service.command.CreateMealChargeCommand;
import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.service.result.ChargeItemResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
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
			.findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceIdForUpdate(
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
		if (existingCharge != null && existingCharge.status() == ChargeStatus.CANCELED) {
			existingCharge.reactivateCanceledCharge(
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
			.findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceIdForUpdate(
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
	public void createOrUpdateCoffeeCharges(List<CreateCoffeeChargeCommand> commands) {
		if (commands.isEmpty()) {
			return;
		}
		CreateCoffeeChargeCommand first = commands.get(0);
		PaymentAccount account = findValidCoffeeAccount(first);
		if (commands.stream().anyMatch(command -> !Objects.equals(command.campusId(), first.campusId())
			|| !Objects.equals(command.paymentAccountId(), first.paymentAccountId()))) {
			throw new BusinessException(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING);
		}
		Map<ChargeIdentity, ChargeItem> existingByIdentity = existingByIdentity(
			first.campusId(), PaymentCategory.COFFEE, commands.stream().map(CreateCoffeeChargeCommand::sourceId).toList()
		);
		List<ChargeItem> newCharges = new ArrayList<>();
		for (CreateCoffeeChargeCommand command : commands) {
			ChargeItem existingCharge = existingByIdentity.get(new ChargeIdentity(command.userId(), command.sourceId()));
			if (existingCharge != null && existingCharge.isUnpaid()) {
				existingCharge.updateUnpaidCharge(
					account, command.title(), command.reason(), command.amount(), command.dueDate()
				);
				continue;
			}
			if (existingCharge != null) {
				continue;
			}
			newCharges.add(ChargeItem.create(
				command.campusId(), command.userId(), PaymentCategory.COFFEE, account.id(), account.bankName(),
				account.accountNumber(), account.accountHolder(), ChargeSourceType.POLL_RESPONSE, command.sourceId(),
				command.title(), command.reason(), command.amount(), command.dueDate()
			));
		}
		if (!newCharges.isEmpty()) {
			chargeItemRepository.saveAllCharges(newCharges);
		}
	}

	@Transactional
	public ChargeItemResult createMealCharge(CreateMealChargeCommand command) {
		PaymentAccount account = paymentAccountRepository.findById(command.paymentAccountId())
			.filter(candidate -> !candidate.isDeleted())
			.filter(PaymentAccount::isActive)
			.filter(candidate -> candidate.campusId().equals(command.campusId()))
			.filter(candidate -> candidate.accountType() == PaymentCategory.MEAL)
			.filter(candidate -> candidate.ownerUserId().equals(command.requesterId()))
			.orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PAYMENT_ACCOUNT_NOT_FOUND));
		if (command.amount() <= 0) {
			throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_INVALID_AMOUNT);
		}
		if (chargeItemRepository.findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceId(
			command.campusId(),
			command.userId(),
			PaymentCategory.MEAL,
			ChargeSourceType.POLL_RESPONSE,
			command.pollResponseId()
		).isPresent()) {
			throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_ALREADY_CHARGED);
		}
		return ChargeItemResult.from(chargeItemRepository.save(ChargeItem.create(
			command.campusId(),
			command.userId(),
			PaymentCategory.MEAL,
			account.id(),
			account.bankName(),
			account.accountNumber(),
			account.accountHolder(),
			ChargeSourceType.POLL_RESPONSE,
			command.pollResponseId(),
			command.title(),
			null,
			command.amount(),
			null
		)));
	}

	@Transactional
	public void createMealCharges(List<CreateMealChargeCommand> commands) {
		if (commands.isEmpty()) {
			return;
		}
		CreateMealChargeCommand first = commands.get(0);
		PaymentAccount account = findValidMealAccount(first);
		if (commands.stream().anyMatch(command -> !Objects.equals(command.campusId(), first.campusId())
			|| !Objects.equals(command.requesterId(), first.requesterId())
			|| !Objects.equals(command.paymentAccountId(), first.paymentAccountId()))) {
			throw new BusinessException(ErrorCode.MEAL_PAYMENT_ACCOUNT_NOT_FOUND);
		}
		if (commands.stream().anyMatch(command -> command.amount() <= 0)) {
			throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_INVALID_AMOUNT);
		}
		Map<ChargeIdentity, ChargeItem> existingByIdentity = existingByIdentity(
			first.campusId(), PaymentCategory.MEAL, commands.stream().map(CreateMealChargeCommand::pollResponseId).toList()
		);
		if (commands.stream().anyMatch(command -> existingByIdentity.containsKey(
			new ChargeIdentity(command.userId(), command.pollResponseId())
		))) {
			throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_ALREADY_CHARGED);
		}
		chargeItemRepository.saveAllCharges(commands.stream()
			.map(command -> ChargeItem.create(
				command.campusId(), command.userId(), PaymentCategory.MEAL, account.id(), account.bankName(),
				account.accountNumber(), account.accountHolder(), ChargeSourceType.POLL_RESPONSE,
				command.pollResponseId(), command.title(), null, command.amount(), null
			))
			.toList());
	}

	private Map<ChargeIdentity, ChargeItem> existingByIdentity(
		Long campusId,
		PaymentCategory paymentCategory,
		List<Long> sourceIds
	) {
		return chargeItemRepository.findByCampusIdAndPaymentCategoryAndSourceTypeAndSourceIdInForUpdate(
			campusId, paymentCategory, ChargeSourceType.POLL_RESPONSE, sourceIds
		).stream().collect(Collectors.toMap(
			charge -> new ChargeIdentity(charge.userId(), charge.sourceId()),
			Function.identity()
		));
	}

	private PaymentAccount findValidMealAccount(CreateMealChargeCommand command) {
		return paymentAccountRepository.findById(command.paymentAccountId())
			.filter(candidate -> !candidate.isDeleted())
			.filter(PaymentAccount::isActive)
			.filter(candidate -> candidate.campusId().equals(command.campusId()))
			.filter(candidate -> candidate.accountType() == PaymentCategory.MEAL)
			.filter(candidate -> candidate.ownerUserId().equals(command.requesterId()))
			.orElseThrow(() -> new BusinessException(ErrorCode.MEAL_PAYMENT_ACCOUNT_NOT_FOUND));
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

	private record ChargeIdentity(Long userId, Long sourceId) {
	}
}
