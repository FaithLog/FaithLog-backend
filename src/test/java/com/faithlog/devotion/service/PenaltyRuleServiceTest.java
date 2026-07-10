package com.faithlog.devotion.service;

import com.faithlog.devotion.service.command.CreatePenaltyRuleCommand;
import com.faithlog.devotion.service.command.UpdatePenaltyRuleCommand;
import com.faithlog.devotion.service.result.PenaltyRuleResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.devotion.domain.type.PenaltyCalculationType;
import com.faithlog.devotion.domain.entity.PenaltyRule;
import com.faithlog.devotion.domain.type.PenaltyRuleType;
import com.faithlog.devotion.infrastructure.repository.PenaltyRuleRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
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
class PenaltyRuleServiceTest {

	@Autowired
	private PenaltyRuleService penaltyRuleService;

	@Autowired
	private CampusService campusService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PenaltyRuleRepository penaltyRuleRepository;

	@Test
	void createPenaltyRule_replaces_existing_active_rule_for_same_campus_and_rule_type() {
		User manager = saveUser("penalty-rule-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "90캠");

		PenaltyRuleResult first = penaltyRuleService.createPenaltyRule(new CreatePenaltyRuleCommand(
			campus.campusId(),
			manager.id(),
			PenaltyRuleType.QUIET_TIME,
			PenaltyCalculationType.MISSING_COUNT,
			5,
			0,
			500
		));
		PenaltyRuleResult second = penaltyRuleService.createPenaltyRule(new CreatePenaltyRuleCommand(
			campus.campusId(),
			manager.id(),
			PenaltyRuleType.QUIET_TIME,
			PenaltyCalculationType.MISSING_COUNT,
			4,
			0,
			700
		));

		assertThat(first.isActive()).isTrue();
		assertThat(second.isActive()).isTrue();
		assertThat(penaltyRuleRepository.findById(first.id())).get()
			.extracting(PenaltyRule::isActive)
			.isEqualTo(false);
		assertThat(penaltyRuleRepository.findById(second.id())).get()
			.extracting(PenaltyRule::isActive)
			.isEqualTo(true);
		assertThat(penaltyRuleRepository.findByCampusIdAndRuleTypeAndIsActiveTrue(campus.campusId(), PenaltyRuleType.QUIET_TIME))
			.extracting(PenaltyRule::id)
			.containsExactly(second.id());
	}

	@Test
	void listPenaltyRules_returns_active_and_inactive_rules_for_active_campus_member() {
		User manager = saveUser("penalty-rule-list-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "91캠");
		User member = saveUser("penalty-rule-list-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		PenaltyRuleResult first = penaltyRuleService.createPenaltyRule(defaultCommand(
			campus.campusId(),
			manager.id(),
			PenaltyRuleType.PRAYER,
			500
		));
		PenaltyRuleResult second = penaltyRuleService.createPenaltyRule(defaultCommand(
			campus.campusId(),
			manager.id(),
			PenaltyRuleType.PRAYER,
			600
		));

		List<PenaltyRuleResult> results = penaltyRuleService.listPenaltyRules(campus.campusId(), member.id());

		assertThat(results).extracting(PenaltyRuleResult::id)
			.contains(first.id(), second.id());
		assertThat(results)
			.filteredOn(rule -> rule.id().equals(first.id()))
			.singleElement()
			.extracting(PenaltyRuleResult::isActive)
			.isEqualTo(false);
		assertThat(results)
			.filteredOn(rule -> rule.id().equals(second.id()))
			.singleElement()
			.extracting(PenaltyRuleResult::isActive)
			.isEqualTo(true);
	}

	@Test
	void updatePenaltyRule_changes_amounts_and_active_state_without_changing_type_pair() {
		User manager = saveUser("penalty-rule-update-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "92캠");
		PenaltyRuleResult created = penaltyRuleService.createPenaltyRule(defaultCommand(
			campus.campusId(),
			manager.id(),
			PenaltyRuleType.BIBLE_READING,
			300
		));

		PenaltyRuleResult updated = penaltyRuleService.updatePenaltyRule(new UpdatePenaltyRuleCommand(
			created.id(),
			manager.id(),
			6,
			100,
			400,
			false
		));

		assertThat(updated.ruleType()).isEqualTo(PenaltyRuleType.BIBLE_READING);
		assertThat(updated.calculationType()).isEqualTo(PenaltyCalculationType.MISSING_COUNT);
		assertThat(updated.requiredCount()).isEqualTo(6);
		assertThat(updated.baseAmount()).isEqualTo(100);
		assertThat(updated.amountPerUnit()).isEqualTo(400);
		assertThat(updated.isActive()).isFalse();
	}

	@Test
	void createPenaltyRule_rejects_invalid_type_pair_and_negative_values() {
		User manager = saveUser("penalty-rule-invalid-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "93캠");

		assertThatThrownBy(() -> penaltyRuleService.createPenaltyRule(new CreatePenaltyRuleCommand(
			campus.campusId(),
			manager.id(),
			PenaltyRuleType.SATURDAY_LATE,
			PenaltyCalculationType.MISSING_COUNT,
			0,
			1000,
			100
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.DEVOTION_PENALTY_RULE_INVALID_TYPE_PAIR)
			);

		assertThatThrownBy(() -> penaltyRuleService.createPenaltyRule(new CreatePenaltyRuleCommand(
			campus.campusId(),
			manager.id(),
			PenaltyRuleType.QUIET_TIME,
			PenaltyCalculationType.MISSING_COUNT,
			-1,
			0,
			500
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.DEVOTION_PENALTY_RULE_INVALID_VALUE)
			);
	}

	@Test
	void admin_penalty_rule_apis_require_campus_manager_or_service_admin() {
		User manager = saveUser("penalty-rule-auth-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "94캠");
		User member = saveUser("penalty-rule-auth-member@example.com", UserRole.USER);
		User admin = saveUser("penalty-rule-auth-admin@example.com", UserRole.ADMIN);
		joinCampus(campus, member);

		assertThatThrownBy(() -> penaltyRuleService.createPenaltyRule(defaultCommand(
			campus.campusId(),
			member.id(),
			PenaltyRuleType.PRAYER,
			500
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.DEVOTION_PENALTY_RULE_MANAGE_FORBIDDEN)
			);

		assertThat(penaltyRuleService.createPenaltyRule(defaultCommand(
			campus.campusId(),
			admin.id(),
			PenaltyRuleType.PRAYER,
			500
		)).isActive()).isTrue();
	}

	private CreatePenaltyRuleCommand defaultCommand(
		Long campusId,
		Long requesterId,
		PenaltyRuleType ruleType,
		int amountPerUnit
	) {
		return new CreatePenaltyRuleCommand(
			campusId,
			requesterId,
			ruleType,
			PenaltyCalculationType.MISSING_COUNT,
			5,
			0,
			amountPerUnit
		);
	}

	private CampusCreateResult createCampus(User manager, String name) {
		return campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			name,
			"분당",
			"분당 " + name
		));
	}

	private void joinCampus(CampusCreateResult campus, User user) {
		campusService.joinCampus(new JoinCampusCommand(user.id(), campus.inviteCode()));
	}

	private User saveUser(String email, UserRole role) {
		User user = userRepository.saveAndFlush(User.create("벌금규칙테스트", email, "encoded-password"));
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}
}
