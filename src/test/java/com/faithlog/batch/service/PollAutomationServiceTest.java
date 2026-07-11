package com.faithlog.batch.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.billing.service.BillingService;
import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.campus.service.command.AssignCoffeeDutyCommand;
import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.poll.service.command.CreatePollTemplateCommand;
import com.faithlog.poll.service.command.CreatePollTemplateOptionCommand;
import com.faithlog.poll.service.PollService;
import com.faithlog.poll.service.result.PollTemplateResult;
import com.faithlog.poll.service.PollTemplateService;
import com.faithlog.poll.service.command.RespondToPollCommand;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollOption;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.support.NotificationConcurrencyTestConfig.InMemoryNotificationConcurrencyPort;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PollAutomationServiceTest {

	@Autowired
	private PollAutomationService pollAutomationService;

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
	private PollRepository pollRepository;

	@Autowired
	private PollOptionRepository pollOptionRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Autowired
	private InMemoryNotificationConcurrencyPort notificationConcurrencyPort;

	@AfterEach
	void resetNotificationConcurrencyPort() {
		notificationConcurrencyPort.reset();
	}

	@Test
	void createDuePolls_creates_only_active_auto_enabled_templates_and_copies_options() {
		User manager = saveUser("batch-template-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "24자동생성캠");
		PollTemplateResult enabled = createTemplate(campus.campusId(), manager.id(), "자동 투표", true);
		createTemplate(campus.campusId(), manager.id(), "수동 투표", false);

		int created = pollAutomationService.createDuePolls(mondayAt(11, 0).toInstant());

		assertThat(created).isEqualTo(1);
		assertThat(pollRepository.findAll())
			.filteredOn(poll -> poll.templateId() != null)
			.hasSize(1)
			.first()
			.satisfies(poll -> {
				assertThat(poll.templateId()).isEqualTo(enabled.id());
				assertThat(poll.title()).isEqualTo("자동 투표");
				assertThat(poll.pollType()).isEqualTo(PollType.CUSTOM);
				assertThat(poll.selectionType()).isEqualTo(SelectionType.SINGLE);
				assertThat(poll.status()).isEqualTo(PollStatus.OPEN);
				assertThat(poll.isAnonymous()).isFalse();
				assertThat(poll.allowUserOptionAdd()).isFalse();
				assertThat(poll.chargeGenerationType()).isEqualTo(ChargeGenerationType.NONE);
				assertThat(poll.paymentCategory()).isNull();
				assertThat(poll.paymentAccountId()).isNull();
				assertThat(poll.startsAt()).isEqualTo(mondayAt(11, 0).toInstant());
				assertThat(poll.endsAt()).isEqualTo(mondayAt(12, 0).toInstant());
				assertThat(poll.createdBy()).isNull();
			});
		Poll poll = pollRepository.findAll().stream()
			.filter(item -> enabled.id().equals(item.templateId()))
			.findFirst()
			.orElseThrow();
		assertThat(pollOptionRepository.findByPollIdOrderBySortOrderAsc(poll.id()))
			.extracting(PollOption::content, PollOption::composeMenuCode, PollOption::priceAmount, PollOption::sortOrder)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple("참석", null, 0, 1),
				org.assertj.core.groups.Tuple.tuple("불참", null, 0, 2)
			);
	}

	@Test
	void createDuePolls_excludes_deactivated_auto_enabled_template() {
		User manager = saveUser("batch-inactive-template-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "152비활성자동템플릿캠");
		PollTemplateResult template = createTemplate(campus.campusId(), manager.id(), "비활성 자동 투표", true);
		pollTemplateService.deactivateTemplate(campus.campusId(), template.id(), manager.id());

		int created = pollAutomationService.createDuePolls(mondayAt(11, 0).toInstant());

		assertThat(created).isZero();
		assertThat(pollRepository.findAll()).noneMatch(poll -> template.id().equals(poll.templateId()));
	}

	@Test
	void createDuePolls_does_not_duplicate_same_campus_template_week() {
		User manager = saveUser("batch-duplicate-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "24중복방지캠");
		PollTemplateResult template = createTemplate(campus.campusId(), manager.id(), "주간 투표", true);

		int first = pollAutomationService.createDuePolls(mondayAt(11, 0).toInstant());
		int second = pollAutomationService.createDuePolls(mondayAt(11, 1).toInstant());

		assertThat(first).isEqualTo(1);
		assertThat(second).isZero();
		assertThat(pollRepository.findAll())
			.filteredOn(poll -> template.id().equals(poll.templateId()))
			.hasSize(1);
	}

	@Test
	void createDuePolls_fails_closed_when_redis_lock_is_unavailable() {
		User manager = saveUser("batch-lock-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "24락장애캠");
		createTemplate(campus.campusId(), manager.id(), "락 테스트 투표", true);
		notificationConcurrencyPort.fail();

		int created = pollAutomationService.createDuePolls(mondayAt(11, 0).toInstant());

		assertThat(created).isZero();
		assertThat(pollRepository.findAll()).noneMatch(poll -> "락 테스트 투표".equals(poll.title()));
	}

	@Test
	void closeDueCoffeePolls_closes_open_coffee_poll_and_calls_settlement() {
		User manager = saveUser("batch-close-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("batch-close-duty@example.com", UserRole.USER);
		User member = saveUser("batch-close-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "24커피마감캠");
		joinCampus(campus, duty);
		joinCampus(campus, member);
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		Long accountId = createCoffeeAccount(campus.campusId(), duty.id(), duty.id());
		PollTemplateResult template = createCoffeeTemplate(campus.campusId(), duty.id(), accountId);
		pollAutomationService.createDuePolls(mondayAt(11, 0).toInstant());
		Poll poll = pollRepository.findAll().stream()
			.filter(item -> template.id().equals(item.templateId()))
			.findFirst()
			.orElseThrow();
		ReflectionTestUtils.setField(poll, "startsAt", Instant.now().minusSeconds(60));
		ReflectionTestUtils.setField(poll, "endsAt", Instant.now().plusSeconds(3600));
		pollRepository.saveAndFlush(poll);
		Long optionId = pollOptionRepository.findByPollIdOrderBySortOrderAsc(poll.id()).get(0).id();
		pollService.respondToPoll(new RespondToPollCommand(campus.campusId(), poll.id(), member.id(), List.of(optionId), null));

		int closed = pollAutomationService.closeDueCoffeePolls(Instant.now().plusSeconds(7200));
		int closedAgain = pollAutomationService.closeDueCoffeePolls(Instant.now().plusSeconds(7200));

		assertThat(closed).isEqualTo(1);
		assertThat(closedAgain).isZero();
		assertThat(pollRepository.findById(poll.id())).get().extracting(Poll::status).isEqualTo(PollStatus.CLOSED);
		assertThat(chargeItemRepository.findAll())
			.hasSize(1)
			.extracting(ChargeItem::userId, ChargeItem::paymentCategory, ChargeItem::amount)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(member.id(), PaymentCategory.COFFEE, 1800));
	}

	private PollTemplateResult createTemplate(Long campusId, Long managerId, String title, boolean autoCreateEnabled) {
		return pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campusId,
			managerId,
			title,
			PollType.CUSTOM,
			SelectionType.SINGLE,
			ChargeGenerationType.NONE,
			null,
			null,
			false,
			autoCreateEnabled,
			DayOfWeek.MONDAY,
			LocalTime.of(11, 0),
			DayOfWeek.MONDAY,
			LocalTime.of(12, 0),
			List.of(
				new CreatePollTemplateOptionCommand("참석", null, 0, 1),
				new CreatePollTemplateOptionCommand("불참", null, 0, 2)
			)
		));
	}

	private PollTemplateResult createCoffeeTemplate(Long campusId, Long managerId, Long accountId) {
		return pollTemplateService.createTemplate(new CreatePollTemplateCommand(
			campusId,
			managerId,
			"자동 커피 투표",
			PollType.COFFEE,
			SelectionType.SINGLE,
			ChargeGenerationType.OPTION_PRICE,
			PaymentCategory.COFFEE,
			accountId,
			false,
			true,
			DayOfWeek.MONDAY,
			LocalTime.of(11, 0),
			DayOfWeek.MONDAY,
			LocalTime.of(12, 0),
			List.of(
				new CreatePollTemplateOptionCommand("아이스 아메리카노", null, 1800, 1),
				new CreatePollTemplateOptionCommand("아메리카노", null, 1500, 2)
			)
		));
	}

	private ZonedDateTime mondayAt(int hour, int minute) {
		return ZonedDateTime.of(2026, 6, 15, hour, minute, 0, 0, PollAutomationService.SEOUL_ZONE);
	}

	private Long createCoffeeAccount(Long campusId, Long requesterId, Long ownerUserId) {
		return billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId,
			requesterId,
			PaymentCategory.COFFEE,
			"커피 계좌",
			"카카오뱅크",
			"3333-24-000001",
			"커피회계",
			ownerUserId
		)).id();
	}

	private User saveUser(String email, UserRole role) {
		User user = User.create("배치테스트", email, "{noop}1234");
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.save(user);
	}

	private CampusCreateResult createCampus(User manager, String name) {
		return campusService.createCampus(new CreateCampusCommand(manager.id(), name, "분당", name + " 설명"));
	}

	private void joinCampus(CampusCreateResult campus, User user) {
		campusService.joinCampus(new JoinCampusCommand(user.id(), campus.inviteCode()));
	}
}
