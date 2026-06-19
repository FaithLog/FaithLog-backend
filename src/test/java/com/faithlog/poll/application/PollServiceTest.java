package com.faithlog.poll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.billing.application.BillingService;
import com.faithlog.billing.application.CreatePaymentAccountCommand;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.campus.application.AssignCoffeeDutyCommand;
import com.faithlog.campus.application.CampusCreateResult;
import com.faithlog.campus.application.CampusService;
import com.faithlog.campus.application.CreateCampusCommand;
import com.faithlog.campus.application.JoinCampusCommand;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.CoffeeBrand;
import com.faithlog.poll.domain.CoffeeMenuCatalog;
import com.faithlog.poll.domain.PollOption;
import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.domain.PollTemplate;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
import com.faithlog.poll.infrastructure.jpa.CoffeeBrandRepository;
import com.faithlog.poll.infrastructure.jpa.CoffeeMenuCatalogRepository;
import com.faithlog.poll.infrastructure.jpa.PollOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollRepository;
import com.faithlog.poll.infrastructure.jpa.PollTemplateOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollTemplateRepository;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PollServiceTest {

	@Autowired
	private CoffeeCatalogService coffeeCatalogService;

	@Autowired
	private PollTemplateService pollTemplateService;

	@Autowired
	private PollService pollService;

	@Autowired
	private CampusService campusService;

	@Autowired
	private BillingService billingService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CoffeeBrandRepository coffeeBrandRepository;

	@Autowired
	private CoffeeMenuCatalogRepository coffeeMenuCatalogRepository;

	@Autowired
	private PollTemplateRepository pollTemplateRepository;

	@Autowired
	private PollTemplateOptionRepository pollTemplateOptionRepository;

	@Autowired
	private PollRepository pollRepository;

	@Autowired
	private PollOptionRepository pollOptionRepository;

	@Test
	void coffee_catalog_seed_contains_compose_brand_and_user_approved_2026_menu_prices() {
		List<CoffeeBrandResult> brands = coffeeCatalogService.listBrands();

		assertThat(brands).extracting(CoffeeBrandResult::brandCode)
			.containsExactly("COMPOSE_COFFEE");
		CoffeeBrand compose = coffeeBrandRepository.findByBrandCode("COMPOSE_COFFEE").orElseThrow();
		assertThat(coffeeCatalogService.listActiveMenus(compose.id()))
			.extracting(CoffeeMenuResult::name, CoffeeMenuResult::priceAmount)
			.contains(
				org.assertj.core.groups.Tuple.tuple("아메리카노", 1500),
				org.assertj.core.groups.Tuple.tuple("아이스 아메리카노", 1800),
				org.assertj.core.groups.Tuple.tuple("아이스티", 3000),
				org.assertj.core.groups.Tuple.tuple("카페라떼", 2900),
				org.assertj.core.groups.Tuple.tuple("플레인 밀크쉐이크", 4200),
				org.assertj.core.groups.Tuple.tuple("쫀득카노", 5800)
			);
		assertThat(coffeeCatalogService.listActiveMenus(compose.id())).hasSizeGreaterThanOrEqualTo(60);
	}

	@Test
	void default_coffee_template_seed_contains_five_default_options_with_snapshots_after_campus_creation() {
		User manager = saveUser("poll-seed-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "137캠");

		PollTemplate template = pollTemplateRepository.findByCampusIdAndPollTypeAndIsDefaultTrue(campus.campusId(), PollType.COFFEE)
			.orElseThrow();

		assertThat(template.title()).isEqualTo("커피 주문 투표");
		assertThat(template.selectionType()).isEqualTo(SelectionType.SINGLE);
		assertThat(template.chargeGenerationType()).isEqualTo(ChargeGenerationType.OPTION_PRICE);
		assertThat(template.paymentCategory()).isEqualTo(PaymentCategory.COFFEE);
		assertThat(pollTemplateOptionRepository.findByTemplateIdOrderBySortOrderAsc(template.id()))
			.extracting(option -> option.content() + ":" + option.priceAmount())
			.containsExactly(
				"아이스 아메리카노:1800",
				"아메리카노:1500",
				"아이스티:3000",
				"아이스 라떼:2900",
				"라떼:2900"
			);
	}

	@Test
	void create_update_deactivate_template_requires_manager_and_stores_menu_snapshots() {
		User manager = saveUser("poll-template-manager@example.com", UserRole.MANAGER);
		User member = saveUser("poll-template-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "138캠");
		joinCampus(campus, member);
		Long americanoMenuId = menuId("AMERICANO_HOT");

		assertThatThrownBy(() -> pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campus.campusId(),
			member.id(),
			"멤버 템플릿",
			PollType.CUSTOM,
			SelectionType.SINGLE,
			ChargeGenerationType.NONE,
			null,
			null,
			false,
			DayOfWeek.MONDAY,
			LocalTime.of(9, 0),
			DayOfWeek.WEDNESDAY,
			LocalTime.of(18, 0),
			List.of(new CreatePollTemplateOptionCommand("참석", null, 0, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_TEMPLATE_MANAGE_FORBIDDEN)
			);

		PollTemplateResult created = pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campus.campusId(),
			manager.id(),
			"커스텀 커피 템플릿",
			PollType.COFFEE,
			SelectionType.SINGLE,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			createCoffeeAccount(campus.campusId(), manager.id()),
			false,
			DayOfWeek.MONDAY,
			LocalTime.of(9, 0),
			DayOfWeek.WEDNESDAY,
			LocalTime.of(18, 0),
			List.of(new CreatePollTemplateOptionCommand(null, americanoMenuId, null, 1))
		));

		assertThat(created.options())
			.extracting(PollTemplateOptionResult::content, PollTemplateOptionResult::composeMenuCode, PollTemplateOptionResult::priceAmount)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("아메리카노", "AMERICANO_HOT", 1500));

		PollTemplateResult updated = pollTemplateService.updateTemplate(new UpdatePollTemplateCommand(
			campus.campusId(),
			created.id(),
			manager.id(),
			"수정된 템플릿",
			SelectionType.MULTIPLE,
			ChargeGenerationType.NONE,
			null,
			null,
			true,
			DayOfWeek.TUESDAY,
			LocalTime.of(10, 0),
			DayOfWeek.THURSDAY,
			LocalTime.of(17, 30),
			List.of(
				new CreatePollTemplateOptionCommand("참석", null, 0, 1),
				new CreatePollTemplateOptionCommand("불참", null, 0, 2)
			)
		));

		assertThat(updated.title()).isEqualTo("수정된 템플릿");
		assertThat(updated.selectionType()).isEqualTo(SelectionType.MULTIPLE);
		assertThat(updated.autoCreateEnabled()).isTrue();
		assertThat(updated.options()).extracting(PollTemplateOptionResult::content)
			.containsExactly("참석", "불참");

		PollTemplateResult deactivated = pollTemplateService.deactivateTemplate(campus.campusId(), created.id(), manager.id());

		assertThat(deactivated.isActive()).isFalse();
	}

	@Test
	void template_detail_update_deactivate_rejects_mismatched_campus_scope() {
		User manager = saveUser("poll-template-scope-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campusA = createCampus(manager, "138A캠");
		CampusCreateResult campusB = createCampus(manager, "138B캠");
		PollTemplateResult templateA = pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campusA.campusId(),
			manager.id(),
			"A 캠퍼스 템플릿",
			PollType.CUSTOM,
			SelectionType.SINGLE,
			ChargeGenerationType.NONE,
			null,
			null,
			false,
			DayOfWeek.MONDAY,
			LocalTime.of(9, 0),
			DayOfWeek.MONDAY,
			LocalTime.of(18, 0),
			List.of(new CreatePollTemplateOptionCommand("참석", null, 0, 1))
		));

		assertThatThrownBy(() -> pollTemplateService.getTemplate(campusB.campusId(), templateA.id(), manager.id()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_TEMPLATE_NOT_FOUND)
			);

		assertThatThrownBy(() -> pollTemplateService.updateTemplate(new UpdatePollTemplateCommand(
			campusB.campusId(),
			templateA.id(),
			manager.id(),
			"잘못된 캠퍼스 path 수정",
			SelectionType.MULTIPLE,
			ChargeGenerationType.NONE,
			null,
			null,
			false,
			DayOfWeek.TUESDAY,
			LocalTime.of(10, 0),
			DayOfWeek.TUESDAY,
			LocalTime.of(19, 0),
			List.of(new CreatePollTemplateOptionCommand("불참", null, 0, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_TEMPLATE_NOT_FOUND)
			);

		assertThatThrownBy(() -> pollTemplateService.deactivateTemplate(campusB.campusId(), templateA.id(), manager.id()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_TEMPLATE_NOT_FOUND)
			);
	}

	@Test
	void create_poll_without_template_uses_direct_options_and_template_poll_copies_snapshots() {
		User manager = saveUser("poll-create-manager@example.com", UserRole.MANAGER);
		User coffeeDuty = saveUser("poll-create-duty@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "139캠");
		joinCampus(campus, coffeeDuty);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), coffeeDuty.id()));
		Long coffeeAccountId = createCoffeeAccount(campus.campusId(), manager.id());
		Long latteMenuId = menuId("CAFE_LATTE");

		PollResult direct = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"직접 커피 투표",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			coffeeAccountId,
			Instant.parse("2026-06-22T00:00:00Z"),
			Instant.parse("2026-06-22T09:00:00Z"),
			List.of(new CreatePollOptionCommand(null, latteMenuId, null, 1))
		));

		assertThat(direct.options())
			.extracting(PollOptionResult::content, PollOptionResult::composeMenuCode, PollOptionResult::priceAmount)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("카페라떼", "CAFE_LATTE", 2900));

		PollTemplate template = pollTemplateRepository.findByCampusIdAndPollTypeAndIsDefaultTrue(campus.campusId(), PollType.COFFEE)
			.orElseThrow();
		template.connectPaymentAccount(coffeeAccountId);
		PollResult templated = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			template.id(),
			"템플릿 커피 투표",
			PollType.COFFEE,
			null,
			false,
			null,
			null,
			null,
			Instant.parse("2026-06-23T00:00:00Z"),
			Instant.parse("2026-06-23T09:00:00Z"),
			List.of()
		));

		assertThat(templated.options()).extracting(PollOptionResult::content)
			.containsExactly("아이스 아메리카노", "아메리카노", "아이스티", "아이스 라떼", "라떼");
		List<PollOption> savedOptions = pollOptionRepository.findByPollIdOrderBySortOrderAsc(templated.id());
		assertThat(savedOptions).extracting(PollOption::priceAmount)
			.containsExactly(1800, 1500, 3000, 2900, 2900);
		assertThat(pollRepository.findById(templated.id())).get()
			.extracting(poll -> poll.status())
			.isEqualTo(PollStatus.SCHEDULED);
	}

	@Test
	void create_poll_rejects_inactive_template_inactive_menu_missing_coffee_duty_and_missing_account() {
		User manager = saveUser("poll-create-invalid-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "140캠");
		Long coffeeAccountId = createCoffeeAccount(campus.campusId(), manager.id());
		PollTemplate template = pollTemplateRepository.findByCampusIdAndPollTypeAndIsDefaultTrue(campus.campusId(), PollType.COFFEE)
			.orElseThrow();
		template.connectPaymentAccount(coffeeAccountId);
		template.deactivate();

		assertThatThrownBy(() -> pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			template.id(),
			"비활성 템플릿",
			PollType.COFFEE,
			null,
			false,
			null,
			null,
			null,
			Instant.parse("2026-06-24T00:00:00Z"),
			Instant.parse("2026-06-24T09:00:00Z"),
			List.of()
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_TEMPLATE_INACTIVE)
			);

		Long menuId = menuId("CAFE_LATTE");
		CoffeeMenuCatalog menu = coffeeMenuCatalogRepository.findById(menuId).orElseThrow();
		menu.deactivate();
		assertThatThrownBy(() -> pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campus.campusId(),
			manager.id(),
			"비활성 메뉴 템플릿",
			PollType.CUSTOM,
			SelectionType.SINGLE,
			ChargeGenerationType.NONE,
			null,
			null,
			false,
			DayOfWeek.MONDAY,
			LocalTime.of(9, 0),
			DayOfWeek.MONDAY,
			LocalTime.of(18, 0),
			List.of(new CreatePollTemplateOptionCommand(null, menuId, null, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_MENU_INACTIVE)
			);

		assertThatThrownBy(() -> pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"담당자 없음",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			coffeeAccountId,
			Instant.parse("2026-06-25T00:00:00Z"),
			Instant.parse("2026-06-25T09:00:00Z"),
			List.of(new CreatePollOptionCommand("선택", null, 0, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_COFFEE_DUTY_MISSING)
			);

		User duty = saveUser("poll-create-invalid-duty@example.com", UserRole.USER);
		joinCampus(campus, duty);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		assertThatThrownBy(() -> pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"계좌 없음",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			null,
			Instant.parse("2026-06-25T00:00:00Z"),
			Instant.parse("2026-06-25T09:00:00Z"),
			List.of(new CreatePollOptionCommand("선택", null, 0, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);
	}

	private Long menuId(String menuCode) {
		return coffeeMenuCatalogRepository.findByMenuCode(menuCode).orElseThrow().id();
	}

	private Long createCoffeeAccount(Long campusId, Long managerId) {
		return billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId,
			managerId,
			PaymentCategory.COFFEE,
			"커피 계좌",
			"카카오뱅크",
			"3333-37-000001",
			"커피회계",
			null
		)).id();
	}

	private User saveUser(String email, UserRole role) {
		User user = User.create("투표테스트", email, "{noop}1234");
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.save(user);
	}

	private CampusCreateResult createCampus(User manager, String name) {
		return campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			name,
			"분당",
			name + " 설명"
		));
	}

	private void joinCampus(CampusCreateResult campus, User user) {
		campusService.joinCampus(new JoinCampusCommand(user.id(), campus.inviteCode()));
	}
}
