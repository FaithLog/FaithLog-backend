package com.faithlog.billing.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BillingQueryUseCaseServiceStructureTest {

	private static final Path SERVICE_ROOT = Path.of("src/main/java/com/faithlog/billing/service");
	private static final Path MAIN_ROOT = Path.of("src/main/java/com/faithlog");

	@Test
	void billingQueryUseCasesAreSeparatedIntoCohesiveReadOnlyTransactionalServices() {
		Map<String, List<String>> responsibilities = Map.of(
			"MyChargeQueryService.java", List.of(
				"listMyCharges",
				"getMyChargeSummary"
			),
			"AdminChargeQueryService.java", List.of(
				"listAdminCampusCharges",
				"listAdminCampusChargesForMyAccounts",
				"listAdminMemberCharges"
			),
			"PaymentAccountQueryService.java", List.of(
				"listPaymentAccounts",
				"listAdminPaymentAccounts",
				"listAdminPaymentAccounts",
				"requireActivePenaltyAccount"
			)
		);

		assertAll(responsibilities.entrySet().stream().map(entry -> () -> {
			Path source = SERVICE_ROOT.resolve(entry.getKey());
			assertTrue(Files.exists(source), entry.getKey() + "가 필요합니다.");
			String content = read(source);
			entry.getValue().stream().distinct().forEach(method -> {
				long expectedCount = entry.getValue().stream().filter(method::equals).count();
				long actualCount = readOnlyTransactionalMethod(method).matcher(content).results().count();
				assertEquals(
					expectedCount,
					actualCount,
					entry.getKey() + "의 " + method + "가 public @Transactional(readOnly = true) 경계를 직접 가져야 합니다."
				);
			});
		}));
	}

	@Test
	void eachDedicatedQueryServiceOwnsOnlyItsApprovedPublicUseCases() {
		String myCharges = read(SERVICE_ROOT.resolve("MyChargeQueryService.java"));
		String adminCharges = read(SERVICE_ROOT.resolve("AdminChargeQueryService.java"));
		String paymentAccounts = read(SERVICE_ROOT.resolve("PaymentAccountQueryService.java"));

		assertAll(
			() -> assertFalse(myCharges.contains("listAdminCampusCharges(")),
			() -> assertFalse(myCharges.contains("listAdminMemberCharges(")),
			() -> assertFalse(myCharges.contains("listPaymentAccounts(")),
			() -> assertFalse(adminCharges.contains("listMyCharges(")),
			() -> assertFalse(adminCharges.contains("getMyChargeSummary(")),
			() -> assertFalse(adminCharges.contains("listPaymentAccounts(")),
			() -> assertFalse(paymentAccounts.contains("listMyCharges(")),
			() -> assertFalse(paymentAccounts.contains("listAdminCampusCharges(")),
			() -> assertFalse(paymentAccounts.contains("listAdminMemberCharges("))
		);
	}

	@Test
	void controllersAndProductionAdapterUseDedicatedQueryServices() {
		String memberController = read(MAIN_ROOT.resolve("billing/controller/BillingController.java"));
		String adminController = read(MAIN_ROOT.resolve("billing/controller/AdminBillingController.java"));
		String devotionAdapter = read(
			MAIN_ROOT.resolve("devotion/infrastructure/adapter/BillingDevotionPenaltyChargeAdapter.java")
		);

		assertAll(
			() -> assertTrue(memberController.contains("MyChargeQueryService")),
			() -> assertTrue(memberController.contains("myChargeQueryService.listMyCharges(")),
			() -> assertTrue(memberController.contains("myChargeQueryService.getMyChargeSummary(")),
			() -> assertTrue(memberController.contains("PaymentAccountQueryService")),
			() -> assertTrue(memberController.contains("paymentAccountQueryService.listPaymentAccounts(")),
			() -> assertFalse(memberController.contains("BillingQueryService")),
			() -> assertTrue(adminController.contains("AdminChargeQueryService")),
			() -> assertTrue(adminController.contains("adminChargeQueryService.listAdminCampusCharges(")),
			() -> assertTrue(adminController.contains("adminChargeQueryService.listAdminCampusChargesForMyAccounts(")),
			() -> assertTrue(adminController.contains("adminChargeQueryService.listAdminMemberCharges(")),
			() -> assertTrue(adminController.contains("PaymentAccountQueryService")),
			() -> assertTrue(adminController.contains("paymentAccountQueryService.listAdminPaymentAccounts(")),
			() -> assertFalse(adminController.contains("BillingQueryService")),
			() -> assertTrue(devotionAdapter.contains("PaymentAccountQueryService")),
			() -> assertTrue(devotionAdapter.contains("paymentAccountQueryService.requireActivePenaltyAccount(")),
			() -> assertFalse(devotionAdapter.contains("BillingQueryService"))
		);
	}

	@Test
	void compatibilityFacadesDoNotOwnRepositoriesTransactionsOrBusinessRules() {
		String queryFacade = read(SERVICE_ROOT.resolve("BillingQueryService.java"));
		String commandFacade = read(SERVICE_ROOT.resolve("BillingService.java"));

		assertAll(
			() -> assertThinFacade(queryFacade),
			() -> assertTrue(queryFacade.contains("MyChargeQueryService")),
			() -> assertTrue(queryFacade.contains("AdminChargeQueryService")),
			() -> assertTrue(queryFacade.contains("PaymentAccountQueryService")),
			() -> assertThinFacade(commandFacade),
			() -> assertTrue(commandFacade.contains("PaymentAccountQueryService")),
			() -> assertFalse(commandFacade.contains("BillingQueryService"))
		);
	}

	@Test
	void dedicatedQueryServicesDoNotDependOnEachOtherOrCompatibilityFacades() {
		Map<String, List<String>> forbiddenDependencies = Map.of(
			"MyChargeQueryService.java", List.of(
				"AdminChargeQueryService",
				"PaymentAccountQueryService",
				"BillingQueryService",
				"BillingService"
			),
			"AdminChargeQueryService.java", List.of(
				"MyChargeQueryService",
				"PaymentAccountQueryService",
				"BillingQueryService",
				"BillingService"
			),
			"PaymentAccountQueryService.java", List.of(
				"MyChargeQueryService",
				"AdminChargeQueryService",
				"BillingQueryService",
				"BillingService"
			)
		);

		assertAll(forbiddenDependencies.entrySet().stream().map(entry -> () -> {
			String content = read(SERVICE_ROOT.resolve(entry.getKey()));
			entry.getValue().forEach(dependency -> assertFalse(
				content.contains(dependency),
				entry.getKey() + "는 " + dependency + "에 의존하면 안 됩니다."
			));
		}));
	}

	private Pattern readOnlyTransactionalMethod(String method) {
		return Pattern.compile(
			"@Transactional\\([^)]*readOnly\\s*=\\s*true[^)]*\\)\\s+public\\s+[^\\{]+?\\s+"
				+ Pattern.quote(method)
				+ "\\s*\\("
		);
	}

	private void assertThinFacade(String content) {
		assertFalse(content.contains("RepositoryPort"));
		assertFalse(content.contains("@Transactional"));
		assertFalse(content.contains("BusinessException"));
		assertFalse(content.contains("ErrorCode"));
		assertFalse(content.contains("BillingAccessPolicy"));
		assertFalse(content.contains("PaymentAccount.create("));
		assertFalse(content.contains("ChargeItem.create("));
	}

	private String read(Path source) {
		try {
			return Files.readString(source);
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
