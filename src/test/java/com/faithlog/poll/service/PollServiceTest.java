package com.faithlog.poll.service;

import com.faithlog.poll.service.command.AddPollOptionCommand;
import com.faithlog.poll.service.command.CreatePollCommand;
import com.faithlog.poll.service.command.CreatePollCommentCommand;
import com.faithlog.poll.service.command.CreatePollOptionCommand;
import com.faithlog.poll.service.command.CreatePollTemplateCommand;
import com.faithlog.poll.service.command.CreatePollTemplateOptionCommand;
import com.faithlog.poll.service.command.DeletePollCommentCommand;
import com.faithlog.poll.service.command.RespondToPollCommand;
import com.faithlog.poll.service.command.UpdatePollCommentCommand;
import com.faithlog.poll.service.command.UpdatePollTemplateCommand;
import com.faithlog.poll.service.result.CoffeeBrandResult;
import com.faithlog.poll.service.result.CoffeeMenuResult;
import com.faithlog.poll.service.result.PollCommentResult;
import com.faithlog.poll.service.result.PollListItemResult;
import com.faithlog.poll.service.result.PollMissingMemberResult;
import com.faithlog.poll.service.result.PollOptionResult;
import com.faithlog.poll.service.result.PollRespondentResult;
import com.faithlog.poll.service.result.PollResponseResult;
import com.faithlog.poll.service.result.PollResult;
import com.faithlog.poll.service.result.PollResultView;
import com.faithlog.poll.service.result.PollTemplateOptionResult;
import com.faithlog.poll.service.result.PollTemplateResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.service.BillingService;
import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.campus.service.command.AssignCoffeeDutyCommand;
import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.infrastructure.repository.CampusDutyAssignmentRepository;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.entity.CoffeeBrand;
import com.faithlog.poll.domain.entity.CoffeeMenuCatalog;
import com.faithlog.poll.domain.entity.PollOption;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.entity.PollTemplate;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import com.faithlog.poll.infrastructure.repository.PollCommentRepository;
import com.faithlog.poll.infrastructure.repository.CoffeeBrandRepository;
import com.faithlog.poll.infrastructure.repository.CoffeeMenuCatalogRepository;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.poll.infrastructure.repository.PollTemplateOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollTemplateRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class PollServiceTest {

	@Autowired
	private CoffeeCatalogService coffeeCatalogService;

	@Autowired
	private PollTemplateService pollTemplateService;

	@Autowired
	private PollService pollService;

	@Autowired
	private CoffeePollSettlementService coffeePollSettlementService;

	@Autowired
	private CampusService campusService;

	@Autowired
	private BillingService billingService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private CampusDutyAssignmentRepository dutyAssignmentRepository;

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

	@MockitoSpyBean
	private PollResponseRepository pollResponseRepository;

	@Autowired
	private PollResponseOptionRepository pollResponseOptionRepository;

	@Autowired
	private PollCommentRepository pollCommentRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

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
	void campus_creation_does_not_seed_default_coffee_template() {
		User manager = saveUser("poll-seed-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "137캠");

		assertThat(pollTemplateRepository.findByCampusIdAndPollTypeAndIsDefaultTrue(campus.campusId(), PollType.COFFEE))
			.isEmpty();
	}

	@Test
	void default_coffee_template_based_poll_copies_user_option_add_enabled() {
		User manager = saveUser("poll-default-template-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("poll-default-template-duty@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "100기본커피템플릿캠");
		joinCampus(campus, duty);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long accountId = createCoffeeAccount(campus.campusId(), duty.id(), duty.id());
		PollTemplateResult template = createCoffeeTemplate(campus.campusId(), duty.id(), accountId);

		PollResult poll = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			duty.id(),
			template.id(),
			"기본 템플릿 기반 커피 투표",
			PollType.COFFEE,
			null,
			false,
			null,
			null,
			null,
			null,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of()
		));

		assertThat(poll.allowUserOptionAdd()).isTrue();
	}

	@Test
	void create_update_deactivate_template_requires_manager_and_stores_menu_snapshots() {
		User manager = saveUser("poll-template-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("poll-template-duty@example.com", UserRole.USER);
		User member = saveUser("poll-template-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "138캠");
		joinCampus(campus, duty);
		joinCampus(campus, member);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
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

		Long dutyAccountId = createCoffeeAccount(campus.campusId(), duty.id(), duty.id());
		PollTemplateResult created = pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campus.campusId(),
			duty.id(),
			"커스텀 커피 템플릿",
			PollType.COFFEE,
			SelectionType.SINGLE,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			dutyAccountId,
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
			duty.id(),
			"수정된 템플릿",
			SelectionType.MULTIPLE,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			dutyAccountId,
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
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void coffee_duty_cannot_update_persisted_custom_template_with_coffee_request_body() {
		assertCoffeeDutyCannotUpdatePersistedNonCoffeeTemplate(PollType.CUSTOM);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void coffee_duty_cannot_update_persisted_wed_service_template_with_coffee_request_body() {
		assertCoffeeDutyCannotUpdatePersistedNonCoffeeTemplate(PollType.WED_SERVICE);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void coffee_duty_cannot_update_persisted_saturday_leader_template_with_coffee_request_body() {
		assertCoffeeDutyCannotUpdatePersistedNonCoffeeTemplate(PollType.SATURDAY_LEADER);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void persisted_coffee_template_update_requires_requester_owned_active_same_campus_coffee_account() {
		User manager = saveUser("poll-179-account-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("poll-179-account-duty@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "179계좌검증캠");
		CampusCreateResult otherCampus = createCampus(manager, "179타캠퍼스계좌캠");
		joinCampus(campus, duty);
		joinCampus(otherCampus, duty);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(otherCampus.campusId(), manager.id(), duty.id()));
		Long originalDutyAccountId = createCoffeeAccount(campus.campusId(), duty.id(), duty.id());
		PollTemplateResult template = createCoffeeTemplate(campus.campusId(), duty.id(), originalDutyAccountId);
		Long otherUserAccountId = createCoffeeAccount(campus.campusId(), manager.id(), manager.id());
		Long activeDutyAccountId = createCoffeeAccount(campus.campusId(), duty.id(), duty.id());
		Long otherCampusAccountId = createCoffeeAccount(otherCampus.campusId(), duty.id(), duty.id());
		Long penaltyAccountId = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"179 벌금 계좌",
			"테스트은행",
			"test-only-179-penalty",
			"테스트회계",
			manager.id()
		)).id();

		assertCoffeeTemplateAccountUpdateRejected(campus.campusId(), template.id(), duty.id(), null);
		assertCoffeeTemplateAccountUpdateRejected(campus.campusId(), template.id(), duty.id(), otherUserAccountId);
		assertCoffeeTemplateAccountUpdateRejected(campus.campusId(), template.id(), duty.id(), originalDutyAccountId);
		assertCoffeeTemplateAccountUpdateRejected(campus.campusId(), template.id(), duty.id(), otherCampusAccountId);
		assertCoffeeTemplateAccountUpdateRejected(campus.campusId(), template.id(), duty.id(), penaltyAccountId);

		PollTemplateResult updated = updateCoffeeTemplate(campus.campusId(), template.id(), duty.id(), activeDutyAccountId);
		assertThat(updated.paymentAccountId()).isEqualTo(activeDutyAccountId);
		assertThat(updated.title()).isEqualTo("179 계좌 회귀 수정");
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void campus_manager_and_service_admin_can_update_persisted_non_coffee_template() {
		User manager = saveUser("poll-179-manager-success@example.com", UserRole.MANAGER);
		User admin = saveUser("poll-179-admin-success@example.com", UserRole.ADMIN);
		CampusCreateResult campus = createCampus(manager, "179관리자성공캠");
		PollTemplateResult template = createNonCoffeeTemplate(campus.campusId(), manager.id(), PollType.CUSTOM, "관리자 수정 전");

		PollTemplateResult managerUpdated = updateNonCoffeeTemplate(
			campus.campusId(), template.id(), manager.id(), "캠퍼스 관리자 수정"
		);
		PollTemplateResult adminUpdated = updateNonCoffeeTemplate(
			campus.campusId(), template.id(), admin.id(), "서비스 관리자 수정"
		);

		assertThat(managerUpdated.title()).isEqualTo("캠퍼스 관리자 수정");
		assertThat(adminUpdated.title()).isEqualTo("서비스 관리자 수정");
	}

	@Test
	void create_poll_without_template_uses_direct_options_and_template_poll_copies_snapshots() {
		User manager = saveUser("poll-create-manager@example.com", UserRole.MANAGER);
		User coffeeDuty = saveUser("poll-create-duty@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "139캠");
		joinCampus(campus, coffeeDuty);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), coffeeDuty.id()));
		Long coffeeAccountId = createCoffeeAccount(campus.campusId(), coffeeDuty.id(), coffeeDuty.id());
		Long latteMenuId = menuId("CAFE_LATTE");

		PollResult direct = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			coffeeDuty.id(),
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

		PollTemplateResult template = createCoffeeTemplate(campus.campusId(), coffeeDuty.id(), coffeeAccountId);
		PollResult templated = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			coffeeDuty.id(),
			template.id(),
			"템플릿 커피 투표",
			PollType.COFFEE,
			null,
			false,
			null,
			null,
			null,
				Instant.now().plusSeconds(86_400),
				Instant.now().plusSeconds(90_000),
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
	void direct_poll_defaults_user_option_add_by_poll_type_and_respects_explicit_false() {
		User manager = saveUser("poll-direct-default-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("poll-direct-default-duty@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "100직접기본값캠");
		joinCampus(campus, duty);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long accountId = createCoffeeAccount(campus.campusId(), duty.id(), duty.id());

		PollResult coffeeOmitted = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			duty.id(),
			null,
			"커피 생략 기본값 투표",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			null,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			accountId,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("아메리카노", null, 1500, 1))
		));
		PollResult coffeeExplicitFalse = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			duty.id(),
			null,
			"커피 명시 비허용 투표",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			false,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			accountId,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("라떼", null, 2900, 1))
		));
		PollResult customOmitted = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"커스텀 생략 기본값 투표",
			PollType.CUSTOM,
			SelectionType.SINGLE,
			false,
			null,
			ChargeGenerationType.NONE,
			null,
			null,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("참석", null, 0, 1))
		));

		assertThat(coffeeOmitted.allowUserOptionAdd()).isTrue();
		assertThat(coffeeExplicitFalse.allowUserOptionAdd()).isFalse();
		assertThat(customOmitted.allowUserOptionAdd()).isFalse();
	}

	@Test
	void coffee_duty_can_create_and_manage_only_coffee_polls_with_user_option_add_enabled_by_default() {
		User manager = saveUser("poll-duty-access-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("poll-duty-access-duty@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "100커피권한캠");
		joinCampus(campus, duty);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long accountId = createCoffeeAccount(campus.campusId(), duty.id(), duty.id());

		PollResult coffeePoll = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			duty.id(),
			null,
			"담당자 커피 투표",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			null,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			accountId,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("아메리카노", null, 1500, 1))
		));

		assertThat(coffeePoll.pollType()).isEqualTo(PollType.COFFEE);
		assertThat(coffeePoll.allowUserOptionAdd()).isTrue();
		assertThat(pollService.closePoll(campus.campusId(), coffeePoll.id(), duty.id()).status())
			.isEqualTo(PollStatus.CLOSED);
		assertThat(pollService.getMissingMembers(campus.campusId(), coffeePoll.id(), duty.id()))
			.extracting(PollMissingMemberResult::userId)
			.contains(manager.id(), duty.id());
		assertThatThrownBy(() -> pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			duty.id(),
			null,
			"담당자 커스텀 투표",
			PollType.CUSTOM,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.NONE,
			null,
			null,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("참석", null, 0, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_CREATE_FORBIDDEN)
			);
	}

	@Test
	void coffee_poll_creation_requires_requester_owned_active_coffee_account_for_manager_and_duty() {
		User manager = saveUser("poll-112-manager-not-duty@example.com", UserRole.MANAGER);
		User serviceAdmin = saveUser("poll-112-service-admin@example.com", UserRole.ADMIN);
		User duty = saveUser("poll-112-duty@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "112커피투표권한캠");
		joinCampus(campus, duty);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long dutyAccountId = createCoffeeAccount(campus.campusId(), duty.id(), duty.id());
		Long managerAccountId = createCoffeeAccount(campus.campusId(), manager.id(), manager.id());

		assertThatThrownBy(() -> pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"관리자지만 담당자 아님",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			dutyAccountId,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("아메리카노", null, 1500, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);
		assertThatThrownBy(() -> pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			serviceAdmin.id(),
			null,
			"전역 관리자지만 담당자 아님",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			dutyAccountId,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("아메리카노", null, 1500, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);

		PollResult managerCreated = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"관리자 커피 투표",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			managerAccountId,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("아메리카노", null, 1500, 1))
		));
		PollResult dutyCreated = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			duty.id(),
			null,
			"담당자 커피 투표",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			dutyAccountId,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("아메리카노", null, 1500, 1))
		));

		assertThat(managerCreated.paymentAccountId()).isEqualTo(managerAccountId);
		assertThat(dutyCreated.paymentAccountId()).isEqualTo(dutyAccountId);
	}

	@Test
	void campus_manager_can_create_coffee_poll_and_template_with_own_active_coffee_account() {
		User manager = saveUser("poll-114-manager-coffee-account@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "114관리자커피투표캠");
		Long managerAccountId = createCoffeeAccount(campus.campusId(), manager.id(), manager.id());

		PollResult poll = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"관리자 본인 계좌 커피 투표",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			managerAccountId,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("아메리카노", null, 1500, 1))
		));
		PollTemplateResult template = pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campus.campusId(),
			manager.id(),
			"관리자 본인 계좌 커피 템플릿",
			PollType.COFFEE,
			SelectionType.SINGLE,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			managerAccountId,
			true,
			false,
			DayOfWeek.MONDAY,
			LocalTime.of(9, 0),
			DayOfWeek.MONDAY,
			LocalTime.of(18, 0),
			List.of(new CreatePollTemplateOptionCommand("아메리카노", null, 1500, 1))
		));

		assertThat(poll.pollType()).isEqualTo(PollType.COFFEE);
		assertThat(poll.paymentAccountId()).isEqualTo(managerAccountId);
		assertThat(template.paymentAccountId()).isEqualTo(managerAccountId);
	}

	@Test
	void coffee_poll_and_template_require_requester_owned_active_coffee_account() {
		User manager = saveUser("poll-114-account-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("poll-114-account-duty@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "114커피계좌검증캠");
		joinCampus(campus, duty);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long dutyAccountId = createCoffeeAccount(campus.campusId(), duty.id(), duty.id());
		Long managerAccountId = createCoffeeAccount(campus.campusId(), manager.id(), manager.id());
		Long inactiveManagerAccountId = managerAccountId;
		billingService.deactivatePaymentAccount(inactiveManagerAccountId, manager.id());
		Long activeManagerAccountId = createCoffeeAccount(campus.campusId(), manager.id(), manager.id());

		assertThatThrownBy(() -> pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"다른 사용자 계좌 커피 투표",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			dutyAccountId,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("아메리카노", null, 1500, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);
		assertThatThrownBy(() -> pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"비활성 계좌 커피 투표",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			inactiveManagerAccountId,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("아메리카노", null, 1500, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);
		assertThatThrownBy(() -> pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campus.campusId(),
			manager.id(),
			"계좌 없는 커피 템플릿",
			PollType.COFFEE,
			SelectionType.SINGLE,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			null,
			true,
			false,
			DayOfWeek.MONDAY,
			LocalTime.of(9, 0),
			DayOfWeek.MONDAY,
			LocalTime.of(18, 0),
			List.of(new CreatePollTemplateOptionCommand("아메리카노", null, 1500, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);
		assertThat(pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"본인 활성 계좌 커피 투표",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			activeManagerAccountId,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("아메리카노", null, 1500, 1))
		)).paymentAccountId()).isEqualTo(activeManagerAccountId);
	}

	@Test
	void coffee_poll_and_template_reject_unusable_payment_account_for_active_duty() {
		User manager = saveUser("poll-112-account-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("poll-112-account-duty@example.com", UserRole.USER);
		User otherDuty = saveUser("poll-112-account-other@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "112커피계좌권한캠");
		joinCampus(campus, duty);
		joinCampus(campus, otherDuty);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		CampusMember otherDutyMembership = campusMemberRepository.findByCampusIdAndUserId(campus.campusId(), otherDuty.id())
			.orElseThrow();
		ReflectionTestUtils.setField(otherDutyMembership, "campusRole", CampusRole.ELDER);
		campusMemberRepository.saveAndFlush(otherDutyMembership);
		Long otherAccountId = createCoffeeAccount(campus.campusId(), otherDuty.id(), otherDuty.id());
		Long dutyAccountId = createCoffeeAccount(campus.campusId(), duty.id(), duty.id());

		assertThatThrownBy(() -> pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			duty.id(),
			null,
			"다른 담당자 계좌 커피 투표",
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			otherAccountId,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("아메리카노", null, 1500, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);
		assertThatThrownBy(() -> pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campus.campusId(),
			manager.id(),
			"관리자 커피 템플릿",
			PollType.COFFEE,
			SelectionType.SINGLE,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			dutyAccountId,
			true,
			false,
			DayOfWeek.MONDAY,
			LocalTime.of(9, 0),
			DayOfWeek.MONDAY,
			LocalTime.of(18, 0),
			List.of(new CreatePollTemplateOptionCommand("아메리카노", null, 1500, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);

		PollTemplateResult template = pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campus.campusId(),
			duty.id(),
			"담당자 커피 템플릿",
			PollType.COFFEE,
			SelectionType.SINGLE,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			dutyAccountId,
			true,
			false,
			DayOfWeek.MONDAY,
			LocalTime.of(9, 0),
			DayOfWeek.MONDAY,
			LocalTime.of(18, 0),
			List.of(new CreatePollTemplateOptionCommand("아메리카노", null, 1500, 1))
		));

		assertThat(template.paymentAccountId()).isEqualTo(dutyAccountId);
		assertThatThrownBy(() -> pollTemplateService.updateTemplate(new UpdatePollTemplateCommand(
			campus.campusId(),
			template.id(),
			duty.id(),
			"다른 계좌로 수정",
			SelectionType.SINGLE,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			otherAccountId,
			true,
			false,
			DayOfWeek.MONDAY,
			LocalTime.of(9, 0),
			DayOfWeek.MONDAY,
			LocalTime.of(18, 0),
			List.of(new CreatePollTemplateOptionCommand("아메리카노", null, 1500, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);
	}

	@Test
	void direct_current_custom_poll_opens_immediately_and_allows_detail_response_results_and_comment_crud() {
		User manager = saveUser("poll-current-custom-manager@example.com", UserRole.MANAGER);
		User member = saveUser("poll-current-custom-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "72커스텀캠");
		joinCampus(campus, member);

		PollResult poll = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"현재 기간 커스텀 투표",
			PollType.CUSTOM,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.NONE,
			null,
			null,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(
				new CreatePollOptionCommand("참석", null, 0, 1),
				new CreatePollOptionCommand("불참", null, 0, 2)
			)
		));

		assertThat(poll.status()).isEqualTo(PollStatus.OPEN);
		assertThat(pollRepository.findById(poll.id())).get()
			.extracting(saved -> saved.status())
			.isEqualTo(PollStatus.OPEN);
		assertThat(pollService.getPollDetail(campus.campusId(), poll.id(), member.id()).poll().status())
			.isEqualTo(PollStatus.OPEN);

		PollResponseResult response = pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			poll.id(),
			member.id(),
			List.of(poll.options().get(0).id()),
			"현재 기간 응답"
		));
		assertThat(response.optionIds()).containsExactly(poll.options().get(0).id());
		assertThat(pollService.getPollResults(campus.campusId(), poll.id(), member.id()).respondedCount())
			.isEqualTo(1);

		PollCommentResult createdComment = pollService.createComment(new CreatePollCommentCommand(
			campus.campusId(),
			poll.id(),
			member.id(),
			"현재 기간 댓글"
		));
		PollCommentResult updatedComment = pollService.updateComment(new UpdatePollCommentCommand(
			campus.campusId(),
			poll.id(),
			createdComment.commentId(),
			member.id(),
			"현재 기간 댓글 수정"
		));
		pollService.deleteComment(new DeletePollCommentCommand(campus.campusId(), poll.id(), createdComment.commentId(), member.id()));

		assertThat(updatedComment.content()).isEqualTo("현재 기간 댓글 수정");
		assertThat(pollService.listComments(campus.campusId(), poll.id(), member.id()))
			.singleElement()
			.satisfies(comment -> {
				assertThat(comment.commentId()).isEqualTo(createdComment.commentId());
				assertThat(comment.deleted()).isTrue();
			});
	}

	@Test
	void current_coffee_poll_created_from_template_opens_immediately_keeps_response_charge_free_then_settles_once_after_close() {
		User manager = saveUser("poll-current-coffee-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("poll-current-coffee-duty@example.com", UserRole.USER);
		User member = saveUser("poll-current-coffee-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "72커피캠");
		joinCampus(campus, duty);
		joinCampus(campus, member);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long accountId = createCoffeeAccount(campus.campusId(), duty.id(), duty.id());
		PollTemplateResult template = createCoffeeTemplate(campus.campusId(), duty.id(), accountId);

		PollResult poll = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			duty.id(),
			template.id(),
			"현재 기간 템플릿 커피 투표",
			PollType.COFFEE,
			null,
			false,
			null,
			null,
			null,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of()
		));

		assertThat(poll.status()).isEqualTo(PollStatus.OPEN);
		PollResponseResult response = pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			poll.id(),
			member.id(),
			List.of(poll.options().get(0).id()),
			"아이스 아메리카노"
		));
		assertThat(chargesForCampus(campus.campusId())).isEmpty();

		closePoll(poll.id());
		coffeePollSettlementService.settleClosedCoffeePoll(campus.campusId(), poll.id());
		coffeePollSettlementService.settleClosedCoffeePoll(campus.campusId(), poll.id());

		assertThat(chargesForCampus(campus.campusId())).hasSize(1);
		ChargeItem charge = chargesForCampus(campus.campusId()).get(0);
		assertThat(charge.paymentCategory()).isEqualTo(PaymentCategory.COFFEE);
		assertThat(charge.sourceType()).isEqualTo(ChargeSourceType.POLL_RESPONSE);
		assertThat(charge.sourceId()).isEqualTo(response.responseId());
	}

	@Test
	void coffee_poll_close_triggers_settlement_and_custom_poll_close_does_not_create_charges() {
		User manager = saveUser("poll-close-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("poll-close-duty@example.com", UserRole.USER);
		User member = saveUser("poll-close-member@example.com", UserRole.USER);
		User normalMember = saveUser("poll-close-normal@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "97종료캠");
		joinCampus(campus, duty);
		joinCampus(campus, member);
		joinCampus(campus, normalMember);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long accountId = createCoffeeAccount(campus.campusId(), manager.id());
		PollResult poll = createOpenCoffeePoll(campus.campusId(), manager.id(), accountId, "수동 종료 커피 투표");
		pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			poll.id(),
			member.id(),
			List.of(poll.options().get(0).id()),
			"종료 전 응답"
		));

		assertThatThrownBy(() -> pollService.closePoll(campus.campusId(), poll.id(), normalMember.id()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_ADMIN_FORBIDDEN)
			);

		PollResult closed = pollService.closePoll(campus.campusId(), poll.id(), manager.id());

		assertThat(closed.status()).isEqualTo(PollStatus.CLOSED);
		assertThat(closed.endsAt()).isBeforeOrEqualTo(Instant.now());
		assertThat(chargesForCampus(campus.campusId())).hasSize(1);
		assertThat(chargesForCampus(campus.campusId()).get(0)).satisfies(charge -> {
			assertThat(charge.paymentCategory()).isEqualTo(PaymentCategory.COFFEE);
			assertThat(charge.sourceType()).isEqualTo(ChargeSourceType.POLL_RESPONSE);
			assertThat(charge.amount()).isEqualTo(1800);
			assertThat(charge.paymentAccountId()).isEqualTo(accountId);
		});
		assertThat(pollService.getPollDetail(campus.campusId(), poll.id(), member.id()).poll().status())
			.isEqualTo(PollStatus.CLOSED);
		assertThat(pollService.getPollResults(campus.campusId(), poll.id(), member.id()).respondedCount())
			.isEqualTo(1);
		assertThatThrownBy(() -> pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			poll.id(),
			member.id(),
			List.of(poll.options().get(1).id()),
			"종료 후 수정"
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_CLOSED)
			);

		PollResult customPoll = createOpenCustomPoll(campus.campusId(), manager.id(), "정산 없는 커스텀 투표", SelectionType.SINGLE, false, List.of("참석"));
		pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			customPoll.id(),
			member.id(),
			List.of(customPoll.options().get(0).id()),
			null
		));
		long chargeCountBeforeCustomClose = chargesForCampus(campus.campusId()).size();

		pollService.closePoll(campus.campusId(), customPoll.id(), manager.id());

		assertThat(chargesForCampus(campus.campusId())).hasSize((int) chargeCountBeforeCustomClose);
	}

	@Test
	void close_poll_rejects_scheduled_or_already_closed_poll() {
		User manager = saveUser("poll-close-state-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "97상태캠");
		PollResult scheduled = createScheduledCustomPoll(campus.campusId(), manager.id(), "예약 투표", SelectionType.SINGLE, false, List.of("A"));
		PollResult opened = createOpenCustomPoll(campus.campusId(), manager.id(), "진행 투표", SelectionType.SINGLE, false, List.of("A"));
		pollService.closePoll(campus.campusId(), opened.id(), manager.id());

		assertThatThrownBy(() -> pollService.closePoll(campus.campusId(), scheduled.id(), manager.id()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_CLOSE_NOT_ALLOWED)
			);
		assertThatThrownBy(() -> pollService.closePoll(campus.campusId(), opened.id(), manager.id()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_CLOSE_NOT_ALLOWED)
			);
	}

	@Test
	void template_user_option_setting_is_copied_to_poll_and_direct_poll_can_override_it() {
		User manager = saveUser("poll-user-option-template-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "97템플릿옵션캠");
		PollTemplateResult template = pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campus.campusId(),
			manager.id(),
			"사용자 항목 허용 템플릿",
			PollType.CUSTOM,
			SelectionType.SINGLE,
			ChargeGenerationType.NONE,
			null,
			null,
			true,
			false,
			DayOfWeek.MONDAY,
			LocalTime.of(9, 0),
			DayOfWeek.MONDAY,
			LocalTime.of(18, 0),
			List.of(new CreatePollTemplateOptionCommand("기본", null, 0, 1))
		));

		PollResult templated = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			template.id(),
			"템플릿 기반 투표",
			PollType.CUSTOM,
			null,
			false,
			null,
			null,
			null,
			null,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of()
		));
		PollResult direct = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"직접 허용 투표",
			PollType.CUSTOM,
			SelectionType.SINGLE,
			false,
			true,
			ChargeGenerationType.NONE,
			null,
			null,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("A", null, 0, 1))
		));

		assertThat(template.allowUserOptionAdd()).isTrue();
		assertThat(templated.allowUserOptionAdd()).isTrue();
		assertThat(direct.allowUserOptionAdd()).isTrue();
	}

	@Test
	void active_member_can_add_user_option_when_poll_allows_it_and_response_still_uses_option_ids() {
		User manager = saveUser("poll-user-option-manager@example.com", UserRole.MANAGER);
		User member = saveUser("poll-user-option-member@example.com", UserRole.USER);
		User outsider = saveUser("poll-user-option-outsider@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "97사용자옵션캠");
		joinCampus(campus, member);
		PollResult poll = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"사용자 항목 추가 투표",
			PollType.CUSTOM,
			SelectionType.SINGLE,
			true,
			true,
			ChargeGenerationType.NONE,
			null,
			null,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(
				new CreatePollOptionCommand("기존 A", null, 0, 1),
				new CreatePollOptionCommand("기존 B", null, 0, 2)
			)
		));

		PollOptionResult added = pollService.addUserOption(new AddPollOptionCommand(
			campus.campusId(),
			poll.id(),
			member.id(),
			"  새 항목  "
		));
		PollResponseResult response = pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			poll.id(),
			member.id(),
			List.of(added.id()),
			"사용자가 추가한 항목 선택"
		));

		assertThat(added.content()).isEqualTo("새 항목");
		assertThat(added.sortOrder()).isEqualTo(3);
		assertThat(added.userAdded()).isTrue();
		assertThat(response.optionIds()).containsExactly(added.id());
		assertThat(pollOptionRepository.findById(added.id())).get()
			.satisfies(option -> {
				assertThat(option.userAdded()).isTrue();
				assertThat(option.createdByUserId()).isEqualTo(member.id());
			});
		assertThatThrownBy(() -> pollService.addUserOption(new AddPollOptionCommand(campus.campusId(), poll.id(), member.id(), "새 항목")))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_OPTION_DUPLICATE_CONTENT)
			);
		assertThatThrownBy(() -> pollService.addUserOption(new AddPollOptionCommand(campus.campusId(), poll.id(), outsider.id(), "외부 항목")))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_ACCESS_FORBIDDEN)
			);
	}

	@Test
	void user_option_add_uses_menu_snapshot_only_for_coffee_polls() {
		User manager = saveUser("poll-user-option-menu-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("poll-user-option-menu-duty@example.com", UserRole.USER);
		User member = saveUser("poll-user-option-menu-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "104메뉴옵션캠");
		joinCampus(campus, duty);
		joinCampus(campus, member);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long accountId = createCoffeeAccount(campus.campusId(), manager.id());
		Long latteMenuId = menuId("CAFE_LATTE");
		PollResult coffeePoll = createOpenCoffeePoll(campus.campusId(), manager.id(), accountId, "메뉴 추가 커피 투표");

		PollOptionResult added = pollService.addUserOption(new AddPollOptionCommand(
			campus.campusId(),
			coffeePoll.id(),
			member.id(),
			null,
			latteMenuId
		));

		assertThat(added.content()).isEqualTo("카페라떼");
		assertThat(added.composeMenuCode()).isEqualTo("CAFE_LATTE");
		assertThat(added.priceAmount()).isEqualTo(2900);
		assertThat(added.userAdded()).isTrue();
		assertThatThrownBy(() -> pollService.addUserOption(new AddPollOptionCommand(
			campus.campusId(),
			coffeePoll.id(),
			member.id(),
			"텍스트 커피 옵션",
			null
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_USER_OPTION_MENU_REQUIRED)
			);

		PollResult customPoll = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"메뉴 금지 커스텀 투표",
			PollType.CUSTOM,
			SelectionType.SINGLE,
			false,
			true,
			ChargeGenerationType.NONE,
			null,
			null,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("기존", null, 0, 1))
		));

		assertThatThrownBy(() -> pollService.addUserOption(new AddPollOptionCommand(
			campus.campusId(),
			customPoll.id(),
			member.id(),
			"커스텀 텍스트",
			latteMenuId
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_USER_OPTION_MENU_NOT_ALLOWED)
			);

		PollOptionResult customAdded = pollService.addUserOption(new AddPollOptionCommand(
			campus.campusId(),
			customPoll.id(),
			member.id(),
			"커스텀 텍스트",
			null
		));

		assertThat(customAdded.content()).isEqualTo("커스텀 텍스트");
		assertThat(customAdded.composeMenuCode()).isNull();
		assertThat(customAdded.priceAmount()).isZero();
	}

	@Test
	void user_option_add_rejects_disabled_scheduled_or_closed_poll() {
		User manager = saveUser("poll-user-option-invalid-manager@example.com", UserRole.MANAGER);
		User member = saveUser("poll-user-option-invalid-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "97옵션거부캠");
		joinCampus(campus, member);
		PollResult disabled = createOpenCustomPoll(campus.campusId(), manager.id(), "비허용 투표", SelectionType.SINGLE, false, List.of("A"));
		PollResult scheduled = createScheduledCustomPoll(campus.campusId(), manager.id(), "예약 투표", SelectionType.SINGLE, false, List.of("A"));
		setPollAllowUserOptionAdd(scheduled.id(), true);
		PollResult closed = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"닫힌 투표",
			PollType.CUSTOM,
			SelectionType.SINGLE,
			false,
			true,
			ChargeGenerationType.NONE,
			null,
			null,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(new CreatePollOptionCommand("A", null, 0, 1))
		));
		pollService.closePoll(campus.campusId(), closed.id(), manager.id());

		assertThatThrownBy(() -> pollService.addUserOption(new AddPollOptionCommand(campus.campusId(), disabled.id(), member.id(), "새 항목")))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_USER_OPTION_ADD_DISABLED)
			);
		assertThatThrownBy(() -> pollService.addUserOption(new AddPollOptionCommand(campus.campusId(), scheduled.id(), member.id(), "새 항목")))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_CLOSED)
			);
		assertThatThrownBy(() -> pollService.addUserOption(new AddPollOptionCommand(campus.campusId(), closed.id(), member.id(), "새 항목")))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_CLOSED)
			);
	}

	@Test
	void future_direct_poll_keeps_scheduled_status() {
		User manager = saveUser("poll-future-status-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "72예약캠");

		PollResult poll = createScheduledCustomPoll(campus.campusId(), manager.id(), "시작 전 투표", SelectionType.SINGLE, false, List.of("A", "B"));

		assertThat(poll.status()).isEqualTo(PollStatus.SCHEDULED);
		assertThat(pollRepository.findById(poll.id())).get()
			.extracting(saved -> saved.status())
			.isEqualTo(PollStatus.SCHEDULED);
	}

	@Test
	void already_ended_direct_poll_does_not_open_and_rejects_response_with_existing_closed_contract() {
		User manager = saveUser("poll-ended-manager@example.com", UserRole.MANAGER);
		User member = saveUser("poll-ended-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "72종료캠");
		joinCampus(campus, member);

		PollResult poll = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
			null,
			"이미 종료된 투표",
			PollType.CUSTOM,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.NONE,
			null,
			null,
			Instant.now().minusSeconds(7200),
			Instant.now().minusSeconds(3600),
			List.of(new CreatePollOptionCommand("A", null, 0, 1))
		));

		assertThat(poll.status()).isNotEqualTo(PollStatus.OPEN);
		assertThatThrownBy(() -> pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			poll.id(),
			member.id(),
			List.of(poll.options().get(0).id()),
			null
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_CLOSED)
			);
	}

	@Test
	void create_poll_rejects_inactive_template_inactive_menu_missing_coffee_duty_and_missing_account() {
		User manager = saveUser("poll-create-invalid-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("poll-create-invalid-duty@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "140캠");
		joinCampus(campus, duty);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long coffeeAccountId = createCoffeeAccount(campus.campusId(), duty.id(), duty.id());
		PollTemplateResult templateResult = createCoffeeTemplate(campus.campusId(), duty.id(), coffeeAccountId);
		PollTemplate template = pollTemplateRepository.findById(templateResult.id()).orElseThrow();
		template.deactivate();

		assertThatThrownBy(() -> pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			duty.id(),
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
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);

		assertThatThrownBy(() -> pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			duty.id(),
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

	@Test
	void respond_to_single_poll_stores_option_ids_and_updates_existing_response_options() {
		User manager = saveUser("poll-response-manager@example.com", UserRole.MANAGER);
		User member = saveUser("poll-response-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "141캠");
		joinCampus(campus, member);
		PollResult poll = createOpenCustomPoll(campus.campusId(), manager.id(), "수요예배", SelectionType.SINGLE, false, List.of("참석", "불참"));
		List<Long> optionIds = poll.options().stream().map(PollOptionResult::id).toList();

		PollResponseResult created = pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			poll.id(),
			member.id(),
			List.of(optionIds.get(0)),
			"참석합니다"
		));
		PollResponseResult updated = pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			poll.id(),
			member.id(),
			List.of(optionIds.get(1)),
			"불참으로 수정합니다"
		));

		assertThat(updated.responseId()).isEqualTo(created.responseId());
		assertThat(updated.optionIds()).containsExactly(optionIds.get(1));
		assertThat(pollResponseRepository.findByPollIdAndUserId(poll.id(), member.id())).isPresent();
		assertThat(pollResponseOptionRepository.findByResponseIdOrderByIdAsc(updated.responseId()))
			.extracting(responseOption -> responseOption.optionId())
			.containsExactly(optionIds.get(1));
	}

	@Test
	void responding_again_with_same_option_ids_does_not_create_duplicate_rows_or_unique_conflict() {
		User manager = saveUser("poll-response-same-manager@example.com", UserRole.MANAGER);
		User member = saveUser("poll-response-same-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "141-1캠");
		joinCampus(campus, member);
		PollResult poll = createOpenCustomPoll(campus.campusId(), manager.id(), "같은 선택지 재저장", SelectionType.SINGLE, false, List.of("참석", "불참"));
		Long optionId = poll.options().get(0).id();

		PollResponseResult created = pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			poll.id(),
			member.id(),
			List.of(optionId),
			"처음 저장"
		));
		PollResponseResult updated = pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			poll.id(),
			member.id(),
			List.of(optionId),
			"같은 선택지 재저장"
		));
		pollResponseOptionRepository.flush();

		assertThat(updated.responseId()).isEqualTo(created.responseId());
		assertThat(pollResponseOptionRepository.findByResponseIdOrderByIdAsc(updated.responseId()))
			.extracting(responseOption -> responseOption.optionId())
			.containsExactly(optionId);
	}

	@Test
	void poll_response_rejects_invalid_option_ids_and_closed_poll() {
		User manager = saveUser("poll-response-invalid-manager@example.com", UserRole.MANAGER);
		User member = saveUser("poll-response-invalid-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "142캠");
		joinCampus(campus, member);
		PollResult single = createOpenCustomPoll(campus.campusId(), manager.id(), "단일", SelectionType.SINGLE, false, List.of("A", "B"));
		PollResult multiple = createOpenCustomPoll(campus.campusId(), manager.id(), "복수", SelectionType.MULTIPLE, false, List.of("C", "D"));
		List<Long> singleOptions = single.options().stream().map(PollOptionResult::id).toList();
		List<Long> multipleOptions = multiple.options().stream().map(PollOptionResult::id).toList();

		assertThatThrownBy(() -> pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), single.id(), member.id(), List.of(), null)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_RESPONSE_INVALID_SELECTION_COUNT)
			);
		assertThatThrownBy(() -> pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), single.id(), member.id(), singleOptions, null)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_RESPONSE_INVALID_SELECTION_COUNT)
			);
		assertThatThrownBy(() -> pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), multiple.id(), member.id(), List.of(multipleOptions.get(0), multipleOptions.get(0)), null)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_RESPONSE_DUPLICATE_OPTION)
			);
		assertThatThrownBy(() -> pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), multiple.id(), member.id(), List.of(singleOptions.get(0)), null)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_OPTION_NOT_FOUND)
			);

		closePoll(multiple.id());
		assertThatThrownBy(() -> pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), multiple.id(), member.id(), List.of(multipleOptions.get(0)), null)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_CLOSED)
			);
	}

	@Test
	void scheduled_poll_rejects_response_and_comment_writes_with_closed_error_contract() {
		User manager = saveUser("poll-scheduled-write-manager@example.com", UserRole.MANAGER);
		User member = saveUser("poll-scheduled-write-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "142-1캠");
		joinCampus(campus, member);
		PollResult scheduled = createScheduledCustomPoll(campus.campusId(), manager.id(), "예약 투표", SelectionType.SINGLE, false, List.of("A", "B"));

		assertThatThrownBy(() -> pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			scheduled.id(),
			member.id(),
			List.of(scheduled.options().get(0).id()),
			null
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_CLOSED)
			);
		assertThatThrownBy(() -> pollService.createComment(new CreatePollCommentCommand(campus.campusId(), scheduled.id(), member.id(), "예약 댓글")))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_CLOSED)
			);
	}

	@Test
	void poll_results_hide_respondents_for_anonymous_poll_and_show_them_for_non_anonymous_poll() {
		User manager = saveUser("poll-result-manager@example.com", UserRole.MANAGER);
		User member = saveUser("poll-result-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "143캠");
		joinCampus(campus, member);
		PollResult namedPoll = createOpenCustomPoll(campus.campusId(), manager.id(), "비익명", SelectionType.SINGLE, false, List.of("참석", "불참"));
		PollResult anonymousPoll = createOpenCustomPoll(campus.campusId(), manager.id(), "익명", SelectionType.SINGLE, true, List.of("참석", "불참"));
		Long namedOptionId = namedPoll.options().get(0).id();
		Long anonymousOptionId = anonymousPoll.options().get(0).id();
		pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), namedPoll.id(), member.id(), List.of(namedOptionId), "비익명"));
		pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), anonymousPoll.id(), member.id(), List.of(anonymousOptionId), "익명"));

		PollResultView namedResult = pollService.getPollResults(campus.campusId(), namedPoll.id(), member.id());
		PollResultView anonymousResult = pollService.getPollResults(campus.campusId(), anonymousPoll.id(), manager.id());

		assertThat(namedResult.optionResults()).filteredOn(option -> option.optionId().equals(namedOptionId))
			.singleElement()
			.satisfies(option -> {
				assertThat(option.responseCount()).isEqualTo(1);
				assertThat(option.respondents()).extracting(PollRespondentResult::userId).containsExactly(member.id());
			});
		assertThat(anonymousResult.optionResults()).filteredOn(option -> option.optionId().equals(anonymousOptionId))
			.singleElement()
			.satisfies(option -> {
				assertThat(option.responseCount()).isEqualTo(1);
				assertThat(option.respondents()).isEmpty();
			});
	}

	@Test
	void poll_results_fetch_respondents_without_per_response_user_lookup() {
		User manager = saveUser("poll-result-query-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "투표결과쿼리캠");
		List<User> members = new ArrayList<>();
		for (int index = 0; index < 25; index++) {
			User member = saveUser("poll-result-query-member-" + index + "@example.com", UserRole.USER);
			joinCampus(campus, member);
			members.add(member);
		}
		PollResult poll = createOpenCustomPoll(campus.campusId(), manager.id(), "대규모 비익명", SelectionType.SINGLE, false, List.of("참석", "불참"));
		Long selectedOptionId = poll.options().get(0).id();
		for (User member : members) {
			pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), poll.id(), member.id(), List.of(selectedOptionId), null));
		}
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		entityManager.flush();
		entityManager.clear();
		statistics.clear();

		PollResultView result = pollService.getPollResults(campus.campusId(), poll.id(), manager.id());

		assertThat(result.respondedCount()).isEqualTo(25);
		assertThat(result.optionResults()).filteredOn(option -> option.optionId().equals(selectedOptionId))
			.singleElement()
			.satisfies(option -> assertThat(option.respondents()).hasSize(25));
		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(12);
	}

	@Test
	void poll_list_marks_current_user_responses_without_per_poll_response_lookup() {
		User manager = saveUser("poll-list-n-plus-one-manager@example.com", UserRole.MANAGER);
		User member = saveUser("poll-list-n-plus-one-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "79목록캠");
		joinCampus(campus, member);
		PollResult first = createOpenCustomPoll(campus.campusId(), manager.id(), "첫 번째 투표", SelectionType.SINGLE, false, List.of("A"));
		PollResult second = createOpenCustomPoll(campus.campusId(), manager.id(), "두 번째 투표", SelectionType.SINGLE, false, List.of("A"));
		PollResult third = createOpenCustomPoll(campus.campusId(), manager.id(), "세 번째 투표", SelectionType.SINGLE, false, List.of("A"));
		PollResult fourth = createOpenCustomPoll(campus.campusId(), manager.id(), "네 번째 투표", SelectionType.SINGLE, false, List.of("A"));
		pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), first.id(), member.id(), List.of(first.options().get(0).id()), null));
		pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), third.id(), member.id(), List.of(third.options().get(0).id()), null));
		clearInvocations(pollResponseRepository);

		List<PollListItemResult> results = pollService.listPolls(campus.campusId(), member.id());

		assertThat(results)
			.extracting(PollListItemResult::id, PollListItemResult::responded)
			.contains(
				org.assertj.core.groups.Tuple.tuple(first.id(), true),
				org.assertj.core.groups.Tuple.tuple(second.id(), false),
				org.assertj.core.groups.Tuple.tuple(third.id(), true),
				org.assertj.core.groups.Tuple.tuple(fourth.id(), false)
			);
		verify(pollResponseRepository).findByPollIdInAndUserId(any(), eq(member.id()));
		verify(pollResponseRepository, never()).findByPollIdAndUserId(anyLong(), anyLong());
	}

	@Test
	void poll_visibility_uses_three_day_member_window_and_seven_day_admin_window() {
		User manager = saveUser("poll-window-manager@example.com", UserRole.MANAGER);
		User member = saveUser("poll-window-member@example.com", UserRole.USER);
		User admin = saveUser("poll-window-admin@example.com", UserRole.ADMIN);
		CampusCreateResult campus = createCampus(manager, "144캠");
		joinCampus(campus, member);
		PollResult expiredForMember = createOpenCustomPoll(campus.campusId(), manager.id(), "나흘 전 종료", SelectionType.SINGLE, false, List.of("A"));
		closePollAt(expiredForMember.id(), Instant.now().minusSeconds(4 * 24 * 60 * 60));

		assertThat(pollService.listPolls(campus.campusId(), member.id()))
			.extracting(PollListItemResult::id)
			.doesNotContain(expiredForMember.id());
		assertThatThrownBy(() -> pollService.getPoll(campus.campusId(), expiredForMember.id(), member.id()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_NOT_FOUND)
			);
		assertThat(pollService.listPolls(campus.campusId(), admin.id()))
			.extracting(PollListItemResult::id)
			.contains(expiredForMember.id());

		closePollAt(expiredForMember.id(), Instant.now().minusSeconds(8 * 24 * 60 * 60));
		assertThatThrownBy(() -> pollService.getPollResults(campus.campusId(), expiredForMember.id(), admin.id()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_NOT_FOUND)
			);
	}

	@Test
	void scheduled_future_poll_is_hidden_from_member_list_and_direct_detail() {
		User manager = saveUser("poll-scheduled-visibility-manager@example.com", UserRole.MANAGER);
		User member = saveUser("poll-scheduled-visibility-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "144-1캠");
		joinCampus(campus, member);
		PollResult scheduled = createScheduledCustomPoll(campus.campusId(), manager.id(), "미래 예약 투표", SelectionType.SINGLE, false, List.of("A"));

		assertThat(pollService.listPolls(campus.campusId(), member.id()))
			.extracting(PollListItemResult::id)
			.doesNotContain(scheduled.id());
		assertThatThrownBy(() -> pollService.getPoll(campus.campusId(), scheduled.id(), member.id()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_NOT_FOUND)
			);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void current_scheduled_poll_opens_on_member_list_detail_and_response_with_campus_scope() {
		User manager = saveUser("poll-142-manager@example.com", UserRole.MANAGER);
		User member = saveUser("poll-142-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "142상태동기화캠");
		CampusCreateResult otherCampus = createCampus(manager, "142다른캠");
		joinCampus(campus, member);
		joinCampus(otherCampus, member);
		PollResult currentScheduled = createOpenCustomPoll(campus.campusId(), manager.id(), "현재 기간 SCHEDULED", SelectionType.SINGLE, false, List.of("A", "B"));
		PollResult futureScheduled = createScheduledCustomPoll(campus.campusId(), manager.id(), "시작 전 예약", SelectionType.SINGLE, false, List.of("A"));
		PollResult recentlyEnded = createOpenCustomPoll(campus.campusId(), manager.id(), "마감 후 3일 이내", SelectionType.SINGLE, false, List.of("A"));
		PollResult otherCampusCurrent = createOpenCustomPoll(otherCampus.campusId(), manager.id(), "다른 캠퍼스 현재 기간", SelectionType.SINGLE, false, List.of("A"));
		setPollStatus(currentScheduled.id(), PollStatus.SCHEDULED);
		setPollStatus(otherCampusCurrent.id(), PollStatus.SCHEDULED);
		closePollAt(recentlyEnded.id(), Instant.now().minusSeconds(60));

		List<PollListItemResult> results = pollService.listPolls(campus.campusId(), member.id());

		assertThat(results)
			.extracting(PollListItemResult::id, PollListItemResult::status)
			.contains(
				org.assertj.core.groups.Tuple.tuple(currentScheduled.id(), PollStatus.OPEN),
				org.assertj.core.groups.Tuple.tuple(recentlyEnded.id(), PollStatus.CLOSED)
			);
		assertThat(results).extracting(PollListItemResult::id)
			.doesNotContain(futureScheduled.id(), otherCampusCurrent.id());
		assertThat(pollRepository.findById(currentScheduled.id())).get()
			.extracting(saved -> saved.status())
			.isEqualTo(PollStatus.OPEN);
		assertThat(pollRepository.findById(otherCampusCurrent.id())).get()
			.extracting(saved -> saved.status())
			.isEqualTo(PollStatus.SCHEDULED);

		setPollStatus(currentScheduled.id(), PollStatus.SCHEDULED);

		assertThat(pollService.getPollDetail(campus.campusId(), currentScheduled.id(), member.id()).poll().status())
			.isEqualTo(PollStatus.OPEN);
		assertThat(pollRepository.findById(currentScheduled.id())).get()
			.extracting(saved -> saved.status())
			.isEqualTo(PollStatus.OPEN);

		setPollStatus(currentScheduled.id(), PollStatus.SCHEDULED);

		PollResponseResult response = pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			currentScheduled.id(),
			member.id(),
			List.of(currentScheduled.options().get(0).id()),
			"현재 기간 예약 row 응답"
		));
		assertThat(response.optionIds()).containsExactly(currentScheduled.options().get(0).id());
		assertThat(pollRepository.findById(currentScheduled.id())).get()
			.extracting(saved -> saved.status())
			.isEqualTo(PollStatus.OPEN);

		assertThatThrownBy(() -> pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			recentlyEnded.id(),
			member.id(),
			List.of(recentlyEnded.options().get(0).id()),
			"마감 후 응답"
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_CLOSED)
			);
	}

	@Test
	void admin_can_query_missing_poll_members_from_active_members_only() {
		User manager = saveUser("poll-missing-manager@example.com", UserRole.MANAGER);
		User responded = saveUser("poll-missing-responded@example.com", UserRole.USER);
		User missing = saveUser("poll-missing-member@example.com", UserRole.USER);
		User inactive = saveUser("poll-missing-inactive@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "145캠");
		joinCampus(campus, responded);
		joinCampus(campus, missing);
		joinCampus(campus, inactive);
		deactivateMembership(campus.campusId(), inactive.id());
		PollResult poll = createOpenCustomPoll(campus.campusId(), manager.id(), "미참여", SelectionType.SINGLE, false, List.of("A"));
		pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), poll.id(), responded.id(), List.of(poll.options().get(0).id()), null));
		pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), poll.id(), manager.id(), List.of(poll.options().get(0).id()), null));

		assertThat(pollService.getMissingMembers(campus.campusId(), poll.id(), manager.id()))
			.extracting(PollMissingMemberResult::userId)
			.containsExactly(missing.id());
	}

	@Test
	void poll_comments_allow_open_create_and_author_or_admin_update_delete_only() {
		User manager = saveUser("poll-comment-manager@example.com", UserRole.MANAGER);
		User author = saveUser("poll-comment-author@example.com", UserRole.USER);
		User other = saveUser("poll-comment-other@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "146캠");
		joinCampus(campus, author);
		joinCampus(campus, other);
		PollResult poll = createOpenCustomPoll(campus.campusId(), manager.id(), "댓글", SelectionType.SINGLE, false, List.of("A"));

		PollCommentResult comment = pollService.createComment(new CreatePollCommentCommand(campus.campusId(), poll.id(), author.id(), "첫 댓글"));

		assertThatThrownBy(() -> pollService.updateComment(new UpdatePollCommentCommand(campus.campusId(), poll.id(), comment.commentId(), other.id(), "남의 댓글 수정")))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_COMMENT_FORBIDDEN)
			);
		assertThat(pollService.updateComment(new UpdatePollCommentCommand(campus.campusId(), poll.id(), comment.commentId(), manager.id(), "관리자 수정")).content())
			.isEqualTo("관리자 수정");
		pollService.deleteComment(new DeletePollCommentCommand(campus.campusId(), poll.id(), comment.commentId(), manager.id()));

		assertThat(pollCommentRepository.findById(comment.commentId())).get()
			.satisfies(saved -> assertThat(saved.isDeleted()).isTrue());
		assertThat(pollService.listComments(campus.campusId(), poll.id(), author.id()))
			.singleElement()
			.satisfies(deleted -> {
				assertThat(deleted.deleted()).isTrue();
				assertThat(deleted.content()).isEqualTo("삭제된 댓글입니다.");
			});

		closePoll(poll.id());
		assertThatThrownBy(() -> pollService.createComment(new CreatePollCommentCommand(campus.campusId(), poll.id(), author.id(), "마감 후 댓글")))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_CLOSED)
			);
		PollResult closedPollWithComment = createOpenCustomPoll(campus.campusId(), manager.id(), "마감 댓글", SelectionType.SINGLE, false, List.of("A"));
		PollCommentResult closedComment = pollService.createComment(new CreatePollCommentCommand(campus.campusId(), closedPollWithComment.id(), author.id(), "마감 전 댓글"));
		closePoll(closedPollWithComment.id());
		assertThatThrownBy(() -> pollService.updateComment(new UpdatePollCommentCommand(campus.campusId(), closedPollWithComment.id(), closedComment.commentId(), author.id(), "마감 후 수정")))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_CLOSED)
			);
		assertThatThrownBy(() -> pollService.deleteComment(new DeletePollCommentCommand(campus.campusId(), closedPollWithComment.id(), closedComment.commentId(), author.id())))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_CLOSED)
			);
	}

	@Test
	void open_coffee_poll_response_does_not_create_coffee_charge_at_response_time() {
		User manager = saveUser("coffee-response-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("coffee-response-duty@example.com", UserRole.USER);
		User member = saveUser("coffee-response-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "39응답캠");
		joinCampus(campus, duty);
		joinCampus(campus, member);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long accountId = createCoffeeAccount(campus.campusId(), manager.id());
		PollResult poll = createOpenCoffeePoll(campus.campusId(), manager.id(), accountId, "응답 시점 커피 투표");

		pollService.respondToPoll(new RespondToPollCommand(
			campus.campusId(),
			poll.id(),
			member.id(),
			List.of(poll.options().get(0).id()),
			"아이스 아메리카노"
		));

		assertThat(chargesForCampus(campus.campusId())).isEmpty();
	}

	@Test
	void settle_closed_coffee_poll_creates_charges_from_final_response_options_and_is_idempotent_for_unpaid_charges() {
		User manager = saveUser("coffee-settle-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("coffee-settle-duty@example.com", UserRole.USER);
		User member = saveUser("coffee-settle-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "39정산캠");
		joinCampus(campus, duty);
		joinCampus(campus, member);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long accountId = createCoffeeAccount(campus.campusId(), manager.id());
		PollResult poll = createOpenCoffeePoll(campus.campusId(), manager.id(), accountId, "정산 커피 투표");
		Long firstOptionId = poll.options().get(0).id();
		Long secondOptionId = poll.options().get(1).id();
		pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), poll.id(), member.id(), List.of(firstOptionId), "처음 선택"));
		PollResponseResult finalResponse = pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), poll.id(), member.id(), List.of(secondOptionId), "최종 선택"));
		closePoll(poll.id());

		coffeePollSettlementService.settleClosedCoffeePoll(campus.campusId(), poll.id());
		coffeePollSettlementService.settleClosedCoffeePoll(campus.campusId(), poll.id());

		assertThat(chargesForCampus(campus.campusId())).hasSize(1);
		ChargeItem charge = chargesForCampus(campus.campusId()).get(0);
		assertThat(charge.paymentCategory()).isEqualTo(PaymentCategory.COFFEE);
		assertThat(charge.sourceType()).isEqualTo(ChargeSourceType.POLL_RESPONSE);
		assertThat(charge.sourceId()).isEqualTo(finalResponse.responseId());
		assertThat(charge.amount()).isEqualTo(1500);
		assertThat(charge.title()).isEqualTo("아메리카노");
		assertThat(charge.reason()).isEqualTo("컴포즈커피 주문");
		assertThat(charge.dueDate()).isNull();
		assertThat(charge.paymentAccountId()).isEqualTo(accountId);
		assertThat(charge.bankNameSnapshot()).isEqualTo("카카오뱅크");
		assertThat(charge.accountNumberSnapshot()).isEqualTo("3333-37-000001");
		assertThat(charge.accountHolderSnapshot()).isEqualTo("커피회계");
	}

	@Test
	void settle_closed_coffee_poll_rejects_non_closed_poll_and_skips_non_coffee_poll() {
		User manager = saveUser("coffee-target-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("coffee-target-duty@example.com", UserRole.USER);
		User member = saveUser("coffee-target-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "39대상캠");
		joinCampus(campus, duty);
		joinCampus(campus, member);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long accountId = createCoffeeAccount(campus.campusId(), manager.id());
		PollResult openCoffeePoll = createOpenCoffeePoll(campus.campusId(), manager.id(), accountId, "열린 커피 투표");

		assertThatThrownBy(() -> coffeePollSettlementService.settleClosedCoffeePoll(campus.campusId(), openCoffeePoll.id()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_SETTLEMENT_NOT_CLOSED)
			);

		PollResult customPoll = createOpenCustomPoll(campus.campusId(), manager.id(), "커스텀 투표", SelectionType.SINGLE, false, List.of("A"));
		pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), customPoll.id(), member.id(), List.of(customPoll.options().get(0).id()), null));
		closePoll(customPoll.id());

		coffeePollSettlementService.settleClosedCoffeePoll(campus.campusId(), customPoll.id());

		assertThat(chargesForCampus(campus.campusId())).isEmpty();
	}

	@Test
	void settle_closed_coffee_poll_keeps_terminal_charge_without_overwriting_it() {
		User manager = saveUser("coffee-terminal-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("coffee-terminal-duty@example.com", UserRole.USER);
		User member = saveUser("coffee-terminal-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "39종료캠");
		joinCampus(campus, duty);
		joinCampus(campus, member);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long accountId = createCoffeeAccount(campus.campusId(), manager.id());
		PollResult poll = createOpenCoffeePoll(campus.campusId(), manager.id(), accountId, "종료 청구 커피 투표");
		PollResponseResult response = pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), poll.id(), member.id(), List.of(poll.options().get(0).id()), null));
		ChargeItem terminal = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(),
			member.id(),
			PaymentCategory.COFFEE,
			accountId,
			"기존은행",
			"기존계좌",
			"기존회계",
			ChargeSourceType.POLL_RESPONSE,
			response.responseId(),
			"기존 커피",
			"기존 사유",
			9999,
			null
		));
		terminal.markPaid();
		closePoll(poll.id());

		coffeePollSettlementService.settleClosedCoffeePoll(campus.campusId(), poll.id());

		assertThat(chargesForCampus(campus.campusId())).hasSize(1);
		assertThat(chargeItemRepository.findById(terminal.id())).get().satisfies(charge -> {
			assertThat(charge.status()).isEqualTo(ChargeStatus.PAID);
			assertThat(charge.title()).isEqualTo("기존 커피");
			assertThat(charge.reason()).isEqualTo("기존 사유");
			assertThat(charge.amount()).isEqualTo(9999);
			assertThat(charge.bankNameSnapshot()).isEqualTo("기존은행");
		});
	}

	@Test
	void settle_closed_coffee_poll_fails_without_duty_or_valid_coffee_account_without_inserting_charge_rows() {
		User manager = saveUser("coffee-prereq-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("coffee-prereq-duty@example.com", UserRole.USER);
		User member = saveUser("coffee-prereq-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "39전제캠");
		joinCampus(campus, duty);
		joinCampus(campus, member);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long accountId = createCoffeeAccount(campus.campusId(), manager.id());
		PollResult missingDutyPoll = createOpenCoffeePoll(campus.campusId(), manager.id(), accountId, "담당자 누락 커피 투표");
		pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), missingDutyPoll.id(), member.id(), List.of(missingDutyPoll.options().get(0).id()), null));
		closePoll(missingDutyPoll.id());
		dutyAssignmentRepository.findByCampusIdAndDutyTypeAndIsActiveTrue(campus.campusId(), DutyType.COFFEE)
			.orElseThrow()
			.revoke();

		assertThatThrownBy(() -> coffeePollSettlementService.settleClosedCoffeePoll(campus.campusId(), missingDutyPoll.id()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_COFFEE_DUTY_MISSING)
			);
		assertThat(chargesForCampus(campus.campusId())).isEmpty();

		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long missingAccountPollAccountId = createCoffeeAccount(campus.campusId(), manager.id());
		PollResult missingAccountPoll = createOpenCoffeePoll(campus.campusId(), manager.id(), missingAccountPollAccountId, "계좌 누락 커피 투표");
		pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), missingAccountPoll.id(), member.id(), List.of(missingAccountPoll.options().get(0).id()), null));
		setPollPaymentAccount(missingAccountPoll.id(), null);
		closePoll(missingAccountPoll.id());

		assertThatThrownBy(() -> coffeePollSettlementService.settleClosedCoffeePoll(campus.campusId(), missingAccountPoll.id()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);
		assertThat(chargesForCampus(campus.campusId())).isEmpty();

		Long penaltyAccountId = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"벌금 계좌",
			"신한은행",
			"110-39-000001",
			"벌금회계",
			null
		)).id();
		Long nonCoffeeAccountPollAccountId = createCoffeeAccount(campus.campusId(), manager.id());
		PollResult nonCoffeeAccountPoll = createOpenCoffeePoll(campus.campusId(), manager.id(), nonCoffeeAccountPollAccountId, "비커피 계좌 커피 투표");
		pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), nonCoffeeAccountPoll.id(), member.id(), List.of(nonCoffeeAccountPoll.options().get(0).id()), null));
		setPollPaymentAccount(nonCoffeeAccountPoll.id(), penaltyAccountId);
		closePoll(nonCoffeeAccountPoll.id());

		assertThatThrownBy(() -> coffeePollSettlementService.settleClosedCoffeePoll(campus.campusId(), nonCoffeeAccountPoll.id()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);
		assertThat(chargesForCampus(campus.campusId())).isEmpty();

		Long inactiveAccountId = createCoffeeAccount(campus.campusId(), manager.id());
		PollResult inactiveAccountPoll = createOpenCoffeePoll(campus.campusId(), manager.id(), inactiveAccountId, "비활성 계좌 커피 투표");
		pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), inactiveAccountPoll.id(), member.id(), List.of(inactiveAccountPoll.options().get(0).id()), null));
		closePoll(inactiveAccountPoll.id());
		billingService.deactivatePaymentAccount(inactiveAccountId, manager.id());

		assertThatThrownBy(() -> coffeePollSettlementService.settleClosedCoffeePoll(campus.campusId(), inactiveAccountPoll.id()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);
		assertThat(chargesForCampus(campus.campusId())).isEmpty();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void settle_closed_coffee_poll_rolls_back_all_charge_changes_when_one_response_is_invalid() {
		User manager = saveUser("coffee-rollback-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("coffee-rollback-duty@example.com", UserRole.USER);
		User firstMember = saveUser("coffee-rollback-first@example.com", UserRole.USER);
		User brokenMember = saveUser("coffee-rollback-broken@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "39롤백캠");
		joinCampus(campus, duty);
		joinCampus(campus, firstMember);
		joinCampus(campus, brokenMember);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long accountId = createCoffeeAccount(campus.campusId(), manager.id());
		PollResult poll = createOpenCoffeePoll(campus.campusId(), manager.id(), accountId, "롤백 커피 투표");
		pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), poll.id(), firstMember.id(), List.of(poll.options().get(0).id()), null));
		pollResponseRepository.save(com.faithlog.poll.domain.entity.PollResponse.create(poll.id(), brokenMember.id(), "선택지 누락"));
		closePoll(poll.id());

		assertThatThrownBy(() -> coffeePollSettlementService.settleClosedCoffeePoll(campus.campusId(), poll.id()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_RESPONSE_INVALID_SELECTION_COUNT)
			);

		assertThat(chargesForCampus(campus.campusId())).isEmpty();
	}

	private Long menuId(String menuCode) {
		return coffeeMenuCatalogRepository.findByMenuCode(menuCode).orElseThrow().id();
	}

	private Long createCoffeeAccount(Long campusId, Long managerId) {
		return createCoffeeAccount(campusId, managerId, null);
	}

	private Long createCoffeeAccount(Long campusId, Long requesterId, Long ownerUserId) {
		return billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId,
			requesterId,
			PaymentCategory.COFFEE,
			"커피 계좌",
			"카카오뱅크",
			"3333-37-000001",
			"커피회계",
			ownerUserId
		)).id();
	}

	private PollTemplateResult createCoffeeTemplate(Long campusId, Long requesterId, Long accountId) {
		return pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campusId,
			requesterId,
			"커피 주문 투표",
			PollType.COFFEE,
			SelectionType.SINGLE,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			accountId,
			true,
			false,
			DayOfWeek.SUNDAY,
			LocalTime.of(20, 0),
			DayOfWeek.MONDAY,
			LocalTime.of(9, 0),
			List.of(
				new CreatePollTemplateOptionCommand("아이스 아메리카노", null, 1800, 1),
				new CreatePollTemplateOptionCommand("아메리카노", null, 1500, 2),
				new CreatePollTemplateOptionCommand("아이스티", null, 3000, 3),
				new CreatePollTemplateOptionCommand("아이스 라떼", null, 2900, 4),
				new CreatePollTemplateOptionCommand("라떼", null, 2900, 5)
			)
		));
	}

	private void assertCoffeeDutyCannotUpdatePersistedNonCoffeeTemplate(PollType persistedPollType) {
		String testId = persistedPollType.name().toLowerCase();
		User manager = saveUser("poll-179-" + testId + "-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("poll-179-" + testId + "-duty@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "179" + persistedPollType + "캠");
		joinCampus(campus, duty);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long dutyAccountId = createCoffeeAccount(campus.campusId(), duty.id(), duty.id());
		PollTemplateResult before = pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campus.campusId(),
			manager.id(),
			"관리자 원본 " + persistedPollType,
			persistedPollType,
			SelectionType.SINGLE,
			ChargeGenerationType.NONE,
			null,
			null,
			false,
			false,
			DayOfWeek.MONDAY,
			LocalTime.of(9, 0),
			DayOfWeek.WEDNESDAY,
			LocalTime.of(18, 0),
			List.of(
				new CreatePollTemplateOptionCommand("원본 참석", null, 0, 1),
				new CreatePollTemplateOptionCommand("원본 불참", null, 0, 2)
			)
		));

		assertThatThrownBy(() -> pollTemplateService.updateTemplate(new UpdatePollTemplateCommand(
			campus.campusId(),
			before.id(),
			duty.id(),
			"권한 없는 수정 " + persistedPollType,
			SelectionType.MULTIPLE,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			dutyAccountId,
			true,
			true,
			DayOfWeek.TUESDAY,
			LocalTime.of(10, 30),
			DayOfWeek.THURSDAY,
			LocalTime.of(17, 30),
			List.of(new CreatePollTemplateOptionCommand("변조 선택지", null, 9999, 1))
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.POLL_TEMPLATE_MANAGE_FORBIDDEN)
			);

		PollTemplateResult after = pollTemplateService.getTemplate(campus.campusId(), before.id(), manager.id());
		assertThat(after).isEqualTo(before);
	}

	private PollTemplateResult createNonCoffeeTemplate(Long campusId, Long requesterId, PollType pollType, String title) {
		return pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campusId,
			requesterId,
			title,
			pollType,
			SelectionType.SINGLE,
			ChargeGenerationType.NONE,
			null,
			null,
			false,
			false,
			DayOfWeek.MONDAY,
			LocalTime.of(9, 0),
			DayOfWeek.WEDNESDAY,
			LocalTime.of(18, 0),
			List.of(new CreatePollTemplateOptionCommand("원본 선택지", null, 0, 1))
		));
	}

	private PollTemplateResult updateNonCoffeeTemplate(Long campusId, Long templateId, Long requesterId, String title) {
		return pollTemplateService.updateTemplate(new UpdatePollTemplateCommand(
			campusId,
			templateId,
			requesterId,
			title,
			SelectionType.MULTIPLE,
			ChargeGenerationType.NONE,
			null,
			null,
			true,
			true,
			DayOfWeek.TUESDAY,
			LocalTime.of(10, 0),
			DayOfWeek.THURSDAY,
			LocalTime.of(17, 0),
			List.of(new CreatePollTemplateOptionCommand("관리자 수정 선택지", null, 0, 1))
		));
	}

	private void assertCoffeeTemplateAccountUpdateRejected(
		Long campusId,
		Long templateId,
		Long requesterId,
		Long paymentAccountId
	) {
		assertThatThrownBy(() -> updateCoffeeTemplate(campusId, templateId, requesterId, paymentAccountId))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);
	}

	private PollTemplateResult updateCoffeeTemplate(
		Long campusId,
		Long templateId,
		Long requesterId,
		Long paymentAccountId
	) {
		return pollTemplateService.updateTemplate(new UpdatePollTemplateCommand(
			campusId,
			templateId,
			requesterId,
			"179 계좌 회귀 수정",
			SelectionType.MULTIPLE,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			paymentAccountId,
			true,
			true,
			DayOfWeek.TUESDAY,
			LocalTime.of(10, 0),
			DayOfWeek.THURSDAY,
			LocalTime.of(17, 0),
			List.of(new CreatePollTemplateOptionCommand("계좌 회귀 선택지", null, 1000, 1))
		));
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

	private PollResult createOpenCustomPoll(Long campusId, Long managerId, String title, SelectionType selectionType, boolean anonymous, List<String> optionContents) {
		PollResult poll = pollService.createPoll(new CreatePollCommand(
			campusId,
			managerId,
			null,
			title,
			PollType.CUSTOM,
			selectionType,
			anonymous,
			ChargeGenerationType.NONE,
			null,
			null,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			optionContents.stream()
				.map(content -> new CreatePollOptionCommand(content, null, 0, optionContents.indexOf(content) + 1))
				.toList()
		));
		com.faithlog.poll.domain.entity.Poll savedPoll = pollRepository.findById(poll.id()).orElseThrow();
		ReflectionTestUtils.setField(savedPoll, "status", PollStatus.OPEN);
		pollRepository.saveAndFlush(savedPoll);
		return pollService.getPoll(campusId, poll.id(), managerId);
	}

	private PollResult createOpenCoffeePoll(Long campusId, Long requesterId, Long accountId, String title) {
		PollResult poll = pollService.createPoll(new CreatePollCommand(
			campusId,
			requesterId,
			null,
			title,
			PollType.COFFEE,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			accountId,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			List.of(
				new CreatePollOptionCommand("아이스 아메리카노", null, 1800, 1),
				new CreatePollOptionCommand("아메리카노", null, 1500, 2)
			)
		));
		com.faithlog.poll.domain.entity.Poll savedPoll = pollRepository.findById(poll.id()).orElseThrow();
		ReflectionTestUtils.setField(savedPoll, "status", PollStatus.OPEN);
		pollRepository.saveAndFlush(savedPoll);
		return pollService.getPoll(campusId, poll.id(), requesterId);
	}

	private PollResult createScheduledCustomPoll(Long campusId, Long managerId, String title, SelectionType selectionType, boolean anonymous, List<String> optionContents) {
		return pollService.createPoll(new CreatePollCommand(
			campusId,
			managerId,
			null,
			title,
			PollType.CUSTOM,
			selectionType,
			anonymous,
			ChargeGenerationType.NONE,
			null,
			null,
			Instant.now().plusSeconds(3600),
			Instant.now().plusSeconds(7200),
			optionContents.stream()
				.map(content -> new CreatePollOptionCommand(content, null, 0, optionContents.indexOf(content) + 1))
				.toList()
		));
	}

	private void closePoll(Long pollId) {
		closePollAt(pollId, Instant.now().minusSeconds(60));
	}

	private void closePollAt(Long pollId, Instant endsAt) {
		com.faithlog.poll.domain.entity.Poll poll = pollRepository.findById(pollId).orElseThrow();
		ReflectionTestUtils.setField(poll, "status", PollStatus.CLOSED);
		ReflectionTestUtils.setField(poll, "endsAt", endsAt);
		pollRepository.saveAndFlush(poll);
	}

	private void setPollPaymentAccount(Long pollId, Long paymentAccountId) {
		com.faithlog.poll.domain.entity.Poll poll = pollRepository.findById(pollId).orElseThrow();
		ReflectionTestUtils.setField(poll, "paymentAccountId", paymentAccountId);
		pollRepository.saveAndFlush(poll);
	}

	private void setPollAllowUserOptionAdd(Long pollId, boolean allowUserOptionAdd) {
		com.faithlog.poll.domain.entity.Poll poll = pollRepository.findById(pollId).orElseThrow();
		ReflectionTestUtils.setField(poll, "allowUserOptionAdd", allowUserOptionAdd);
		pollRepository.saveAndFlush(poll);
	}

	private void setPollStatus(Long pollId, PollStatus status) {
		com.faithlog.poll.domain.entity.Poll poll = pollRepository.findById(pollId).orElseThrow();
		ReflectionTestUtils.setField(poll, "status", status);
		pollRepository.saveAndFlush(poll);
	}

	private List<ChargeItem> chargesForCampus(Long campusId) {
		return chargeItemRepository.findAll()
			.stream()
			.filter(charge -> charge.campusId().equals(campusId))
			.toList();
	}

	private void deactivateMembership(Long campusId, Long userId) {
		CampusMember member = campusMemberRepository.findByCampusIdAndUserId(campusId, userId).orElseThrow();
		member.deactivate();
	}
}
