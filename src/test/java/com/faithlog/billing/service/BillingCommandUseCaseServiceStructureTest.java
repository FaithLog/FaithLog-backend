package com.faithlog.billing.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BillingCommandUseCaseServiceStructureTest {

	private static final Path SERVICE_ROOT = Path.of("src/main/java/com/faithlog/billing/service");
	private static final Path MAIN_ROOT = Path.of("src/main/java/com/faithlog");

	@Test
	void billingCommandUseCasesAreSeparatedIntoCohesiveTransactionalServices() {
		Map<String, List<String>> responsibilities = Map.of(
			"PaymentAccountCommandService.java", List.of(
				"createPaymentAccount",
				"deactivatePaymentAccount",
				"activatePenaltyPaymentAccount",
				"deletePaymentAccount"
			),
			"ChargeCreationService.java", List.of(
				"createPenaltyCharge",
				"createOrUpdateCoffeeCharge"
			),
			"ChargeStatusCommandService.java", List.of(
				"completeMyChargePayment",
				"changeChargeStatus"
			)
		);

		assertAll(responsibilities.entrySet().stream().map(entry -> () -> {
			Path source = SERVICE_ROOT.resolve(entry.getKey());
			assertTrue(Files.exists(source), entry.getKey() + "가 필요합니다.");
			String content = read(source);
			entry.getValue().forEach(method -> {
				assertTrue(
					content.contains(" " + method + "("),
					entry.getKey() + "가 " + method + " 책임을 가져야 합니다."
				);
				assertTrue(
					transactionalMethod(method).matcher(content).find(),
					entry.getKey() + "의 " + method + "가 @Transactional 경계를 직접 가져야 합니다."
				);
			});
		}));
	}

	@Test
	void controllersAndProductionAdaptersUseDedicatedCommandServices() {
		String adminController = read(MAIN_ROOT.resolve("billing/controller/AdminBillingController.java"));
		String memberController = read(MAIN_ROOT.resolve("billing/controller/BillingController.java"));
		String devotionAdapter = read(
			MAIN_ROOT.resolve("devotion/infrastructure/adapter/BillingDevotionPenaltyChargeAdapter.java")
		);
		String coffeeAdapter = read(
			MAIN_ROOT.resolve("poll/infrastructure/adapter/BillingCoffeePollChargeAdapter.java")
		);

		assertAll(
			() -> assertTrue(adminController.contains("PaymentAccountCommandService")),
			() -> assertTrue(adminController.contains("paymentAccountCommandService.createPaymentAccount(")),
			() -> assertTrue(adminController.contains("paymentAccountCommandService.deactivatePaymentAccount(")),
			() -> assertTrue(adminController.contains("paymentAccountCommandService.activatePenaltyPaymentAccount(")),
			() -> assertTrue(adminController.contains("paymentAccountCommandService.deletePaymentAccount(")),
			() -> assertTrue(adminController.contains("ChargeStatusCommandService")),
			() -> assertTrue(adminController.contains("chargeStatusCommandService.changeChargeStatus(")),
			() -> assertFalse(adminController.contains("billingService.createPaymentAccount(")),
			() -> assertFalse(adminController.contains("billingService.changeChargeStatus(")),
			() -> assertTrue(memberController.contains("ChargeStatusCommandService")),
			() -> assertTrue(memberController.contains("chargeStatusCommandService.completeMyChargePayment(")),
			() -> assertFalse(memberController.contains("billingService.completeMyChargePayment(")),
			() -> assertTrue(devotionAdapter.contains("ChargeCreationService")),
			() -> assertTrue(devotionAdapter.contains("chargeCreationService.createPenaltyCharge(")),
			() -> assertFalse(devotionAdapter.contains("billingService.createPenaltyCharge(")),
			() -> assertTrue(coffeeAdapter.contains("ChargeCreationService")),
			() -> assertTrue(coffeeAdapter.contains("chargeCreationService.createOrUpdateCoffeeCharge(")),
			() -> assertFalse(coffeeAdapter.contains("billingService.createOrUpdateCoffeeCharge("))
		);
	}

	@Test
	void compatibilityFacadeDoesNotOwnRepositoriesTransactionsOrBusinessRules() {
		String content = read(SERVICE_ROOT.resolve("BillingService.java"));

		assertAll(
			() -> assertFalse(content.contains("RepositoryPort")),
			() -> assertFalse(content.contains("@Transactional")),
			() -> assertFalse(content.contains("BusinessException")),
			() -> assertFalse(content.contains("BillingAccessPolicy")),
			() -> assertFalse(content.contains("ChargeStatusPolicy")),
			() -> assertFalse(content.contains("PaymentAccount.create(")),
			() -> assertFalse(content.contains("ChargeItem.create(")),
			() -> assertTrue(content.contains("PaymentAccountCommandService")),
			() -> assertTrue(content.contains("ChargeCreationService")),
			() -> assertTrue(content.contains("ChargeStatusCommandService"))
		);
	}

	private Pattern transactionalMethod(String method) {
		return Pattern.compile(
			"@Transactional\\s+public\\s+[^\\n{]+\\s+" + Pattern.quote(method) + "\\s*\\("
		);
	}

	private String read(Path source) {
		try {
			return Files.readString(source);
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
