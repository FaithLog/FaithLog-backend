package com.faithlog.poll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeSourceType;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.application.BillingService;
import com.faithlog.billing.application.CreatePaymentAccountCommand;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.billing.infrastructure.jpa.ChargeItemRepository;
import com.faithlog.campus.application.AssignCoffeeDutyCommand;
import com.faithlog.campus.application.CampusCreateResult;
import com.faithlog.campus.application.CampusService;
import com.faithlog.campus.application.CreateCampusCommand;
import com.faithlog.campus.application.JoinCampusCommand;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.DutyType;
import com.faithlog.campus.infrastructure.jpa.CampusDutyAssignmentRepository;
import com.faithlog.campus.infrastructure.jpa.CampusMemberRepository;
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
import com.faithlog.poll.infrastructure.jpa.PollCommentRepository;
import com.faithlog.poll.infrastructure.jpa.CoffeeBrandRepository;
import com.faithlog.poll.infrastructure.jpa.CoffeeMenuCatalogRepository;
import com.faithlog.poll.infrastructure.jpa.PollOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollRepository;
import com.faithlog.poll.infrastructure.jpa.PollResponseOptionRepository;
import com.faithlog.poll.infrastructure.jpa.PollResponseRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
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
		Long accountId = createCoffeeAccount(campus.campusId(), manager.id());
		PollTemplate template = pollTemplateRepository.findByCampusIdAndPollTypeAndIsDefaultTrue(campus.campusId(), PollType.COFFEE)
			.orElseThrow();
		template.connectPaymentAccount(accountId);

		PollResult poll = pollService.createPoll(new CreatePollCommand(
			campus.campusId(),
			manager.id(),
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
		pollResponseRepository.save(com.faithlog.poll.domain.PollResponse.create(poll.id(), brokenMember.id(), "선택지 누락"));
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
		com.faithlog.poll.domain.Poll savedPoll = pollRepository.findById(poll.id()).orElseThrow();
		ReflectionTestUtils.setField(savedPoll, "status", PollStatus.OPEN);
		pollRepository.saveAndFlush(savedPoll);
		return pollService.getPoll(campusId, poll.id(), managerId);
	}

	private PollResult createOpenCoffeePoll(Long campusId, Long managerId, Long accountId, String title) {
		PollResult poll = pollService.createPoll(new CreatePollCommand(
			campusId,
			managerId,
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
		com.faithlog.poll.domain.Poll savedPoll = pollRepository.findById(poll.id()).orElseThrow();
		ReflectionTestUtils.setField(savedPoll, "status", PollStatus.OPEN);
		pollRepository.saveAndFlush(savedPoll);
		return pollService.getPoll(campusId, poll.id(), managerId);
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
		com.faithlog.poll.domain.Poll poll = pollRepository.findById(pollId).orElseThrow();
		ReflectionTestUtils.setField(poll, "status", PollStatus.CLOSED);
		ReflectionTestUtils.setField(poll, "endsAt", endsAt);
		pollRepository.saveAndFlush(poll);
	}

	private void setPollPaymentAccount(Long pollId, Long paymentAccountId) {
		com.faithlog.poll.domain.Poll poll = pollRepository.findById(pollId).orElseThrow();
		ReflectionTestUtils.setField(poll, "paymentAccountId", paymentAccountId);
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
