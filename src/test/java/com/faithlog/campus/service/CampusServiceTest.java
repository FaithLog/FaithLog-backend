package com.faithlog.campus.service;

import com.faithlog.campus.service.command.AssignCoffeeDutyCommand;
import com.faithlog.campus.service.command.AssignMealDutyCommand;
import com.faithlog.campus.service.command.ChangeCampusRoleCommand;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.campus.service.command.UpdateCampusCommand;
import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.result.CampusDetailResult;
import com.faithlog.campus.service.result.CampusMembershipResult;
import com.faithlog.campus.service.result.DutyAssignmentResult;
import com.faithlog.campus.service.result.MyDutyAssignmentResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.billing.infrastructure.repository.PaymentAccountRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.PollTemplateRepository;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class CampusServiceTest {

	@Autowired
	private CampusService campusService;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private PollTemplateRepository pollTemplateRepository;

	@Autowired
	private PaymentAccountRepository paymentAccountRepository;

	@MockitoSpyBean
	private ChargeItemRepository chargeItemRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private DeterministicInviteCodeGenerator inviteCodeGenerator;

	@BeforeEach
	void resetInviteCodeGenerator() {
		inviteCodeGenerator.reset();
	}

	@Test
	void createCampus_retries_invite_code_collision_and_registers_creator_as_active_minister() {
		User manager = saveUser("service-manager@example.com", UserRole.MANAGER);

		CampusCreateResult first = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"20캠",
			"분당",
			"분당 20캠퍼스"
		));
		CampusCreateResult second = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"21캠",
			"분당",
			"분당 21캠퍼스"
		));

		assertThat(first.inviteCode()).isEqualTo("FL-DUPLICATE");
		assertThat(second.inviteCode()).isNotEqualTo(first.inviteCode());
		assertThat(campusRepository.existsByInviteCode("FL-DUPLICATE")).isTrue();
		assertThat(campusRepository.existsByInviteCode(second.inviteCode())).isTrue();
		assertThat(first.myCampusRole()).isEqualTo("MINISTER");
		assertThat(first.membershipStatus()).isEqualTo("ACTIVE");
		assertThat(campusMemberRepository.findByCampusIdAndUserId(first.campusId(), manager.id()))
			.get()
			.satisfies(member -> {
				assertThat(member.campusRole().name()).isEqualTo("MINISTER");
				assertThat(member.status().name()).isEqualTo("ACTIVE");
			});
	}

	@Test
	void createCampus_does_not_create_default_coffee_template_or_recurring_poll_side_effect() {
		User manager = saveUser("campus-no-coffee-template-manager@example.com", UserRole.MANAGER);

		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"112커피템플릿없는캠",
			"분당",
			"커피 기본 템플릿 자동 생성 금지"
		));

		assertThat(pollTemplateRepository.findByCampusIdAndPollTypeAndIsDefaultTrue(campus.campusId(), PollType.COFFEE))
			.isEmpty();
	}

	@Test
	void getMyCampuses_returns_only_active_memberships() {
		User manager = saveUser("active-manager@example.com", UserRole.MANAGER);
		User member = saveUser("active-member@example.com", UserRole.USER);
		CampusCreateResult activeCampus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"22캠",
			"분당",
			"분당 22캠퍼스"
		));
		CampusCreateResult inactiveCampus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"23캠",
			"분당",
			"분당 23캠퍼스"
		));
		campusService.joinCampus(new JoinCampusCommand(member.id(), activeCampus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(member.id(), inactiveCampus.inviteCode()));
		CampusMember inactiveMembership = campusMemberRepository
			.findByCampusIdAndUserId(inactiveCampus.campusId(), member.id())
			.orElseThrow();
		ReflectionTestUtils.setField(inactiveMembership, "status", CampusMemberStatus.INACTIVE);
		campusMemberRepository.saveAndFlush(inactiveMembership);

		List<CampusMembershipResult> memberships = campusService.getMyCampuses(member.id());

		assertThat(memberships)
			.extracting(CampusMembershipResult::campusId)
			.containsExactly(activeCampus.campusId());
		assertThat(memberships.getFirst().status()).isEqualTo("ACTIVE");
	}

	@Test
	void getMyCampuses_fetches_memberships_and_campuses_without_per_membership_lookup() {
		User manager = saveUser("my-campus-query-manager@example.com", UserRole.MANAGER);
		User member = saveUser("my-campus-query-member@example.com", UserRole.USER);
		for (int index = 0; index < 3; index++) {
			CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
				manager.id(),
				"쿼리캠" + index,
				"분당",
				"캠퍼스 목록 조회 쿼리 evidence " + index
			));
			campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		}
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		entityManager.flush();
		entityManager.clear();
		statistics.clear();

		List<CampusMembershipResult> memberships = campusService.getMyCampuses(member.id());

		assertThat(memberships).hasSize(3);
		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
	}

	@Test
	void updateCampus_preserves_invite_code_and_requires_campus_manager_or_service_admin() {
		User manager = saveUser("campus-update-manager@example.com", UserRole.MANAGER);
		User outsider = saveUser("campus-update-outsider@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"수정 전 캠퍼스",
			"분당",
			"수정 전 설명"
		));

		CampusDetailResult updated = campusService.updateCampus(new UpdateCampusCommand(
			manager.id(),
			campus.campusId(),
			"수정 후 캠퍼스",
			"서울",
			"수정 후 설명",
			false
		));

		assertThat(updated.name()).isEqualTo("수정 후 캠퍼스");
		assertThat(updated.region()).isEqualTo("서울");
		assertThat(updated.description()).isEqualTo("수정 후 설명");
		assertThat(updated.isActive()).isFalse();
		assertThat(updated.inviteCode()).isEqualTo(campus.inviteCode());
		assertThatThrownBy(() -> campusService.updateCampus(new UpdateCampusCommand(
			outsider.id(),
			campus.campusId(),
			"권한 없는 수정",
			"서울",
			"권한 없는 수정",
			true
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("캠퍼스 멤버 관리 권한이 없습니다.");
	}

	@Test
	void deleteCampusMember_deactivates_member_when_requester_can_manage_members() {
		User manager = saveUser("delete-manager@example.com", UserRole.MANAGER);
		User elder = saveUser("delete-elder@example.com", UserRole.USER);
		User member = saveUser("delete-member@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"24캠",
			"분당",
			"분당 24캠퍼스"
		));
		CampusMembershipResult elderMembership = campusService.joinCampus(new JoinCampusCommand(elder.id(), campus.inviteCode()));
		CampusMembershipResult memberMembership = campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		updateCampusRole(elderMembership.membershipId(), CampusRole.ELDER);

		campusService.deleteCampusMember(campus.campusId(), memberMembership.membershipId(), elder.id());

		assertThat(campusMemberRepository.findById(memberMembership.membershipId()))
			.get()
			.extracting(CampusMember::status)
			.isEqualTo(CampusMemberStatus.INACTIVE);
	}

	@Test
	void deleteCampusMember_rejects_normal_member_and_allows_admin_without_membership() {
		User manager = saveUser("delete-admin-manager@example.com", UserRole.MANAGER);
		User normalMember = saveUser("delete-normal-member@example.com", UserRole.USER);
		User target = saveUser("delete-target@example.com", UserRole.USER);
		User admin = saveUser("delete-admin@example.com", UserRole.ADMIN);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"25캠",
			"분당",
			"분당 25캠퍼스"
		));
		campusService.joinCampus(new JoinCampusCommand(normalMember.id(), campus.inviteCode()));
		CampusMembershipResult targetMembership = campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));

		assertThatThrownBy(() -> campusService.deleteCampusMember(campus.campusId(), targetMembership.membershipId(), normalMember.id()))
			.isInstanceOf(BusinessException.class)
			.hasMessage("캠퍼스 멤버 관리 권한이 없습니다.");

		campusService.deleteCampusMember(campus.campusId(), targetMembership.membershipId(), admin.id());

		assertThat(campusMemberRepository.findById(targetMembership.membershipId()))
			.get()
			.extracting(CampusMember::status)
			.isEqualTo(CampusMemberStatus.INACTIVE);
	}

	@Test
	void joinCampus_reactivates_inactive_membership_as_member() {
		User manager = saveUser("reactivate-manager@example.com", UserRole.MANAGER);
		User member = saveUser("reactivate-member@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"26캠",
			"분당",
			"분당 26캠퍼스"
		));
		CampusMembershipResult membership = campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		campusService.deleteCampusMember(campus.campusId(), membership.membershipId(), manager.id());

		CampusMembershipResult rejoined = campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));

		assertThat(rejoined.membershipId()).isEqualTo(membership.membershipId());
		assertThat(rejoined.campusRole()).isEqualTo("MEMBER");
		assertThat(rejoined.status()).isEqualTo("ACTIVE");
	}

	@Test
	void changeCampusRole_allows_same_or_lower_role_assignment_by_campus_hierarchy() {
		User manager = saveUser("role-manager@example.com", UserRole.MANAGER);
		User minister = saveUser("role-minister@example.com", UserRole.USER);
		User elder = saveUser("role-elder@example.com", UserRole.USER);
		User leader = saveUser("role-leader@example.com", UserRole.USER);
		User member = saveUser("role-member@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"30캠",
			"분당",
			"분당 30캠퍼스"
		));
		CampusMembershipResult ministerMembership = campusService.joinCampus(new JoinCampusCommand(minister.id(), campus.inviteCode()));
		CampusMembershipResult elderMembership = campusService.joinCampus(new JoinCampusCommand(elder.id(), campus.inviteCode()));
		CampusMembershipResult leaderMembership = campusService.joinCampus(new JoinCampusCommand(leader.id(), campus.inviteCode()));
		CampusMembershipResult memberMembership = campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		updateCampusRole(ministerMembership.membershipId(), CampusRole.MINISTER);
		updateCampusRole(elderMembership.membershipId(), CampusRole.ELDER);
		updateCampusRole(leaderMembership.membershipId(), CampusRole.CAMPUS_LEADER);

		assertThat(campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), memberMembership.membershipId(), minister.id(), CampusRole.MINISTER
		)).campusRole()).isEqualTo("MINISTER");
		assertThat(campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), memberMembership.membershipId(), minister.id(), CampusRole.ELDER
		)).campusRole()).isEqualTo("ELDER");
		assertThat(campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), memberMembership.membershipId(), minister.id(), CampusRole.CAMPUS_LEADER
		)).campusRole()).isEqualTo("CAMPUS_LEADER");
		assertThat(campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), memberMembership.membershipId(), minister.id(), CampusRole.MEMBER
		)).campusRole()).isEqualTo("MEMBER");

		assertThat(campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), memberMembership.membershipId(), elder.id(), CampusRole.ELDER
		)).campusRole()).isEqualTo("ELDER");
		assertThat(campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), memberMembership.membershipId(), elder.id(), CampusRole.CAMPUS_LEADER
		)).campusRole()).isEqualTo("CAMPUS_LEADER");
		assertThat(campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), memberMembership.membershipId(), elder.id(), CampusRole.MEMBER
		)).campusRole()).isEqualTo("MEMBER");

		assertThat(campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), memberMembership.membershipId(), leader.id(), CampusRole.CAMPUS_LEADER
		)).campusRole()).isEqualTo("CAMPUS_LEADER");
		assertThat(campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), memberMembership.membershipId(), leader.id(), CampusRole.MEMBER
		)).campusRole()).isEqualTo("MEMBER");
	}

	@Test
	void changeCampusRole_rejects_member_higher_target_role_and_manager_role_only() {
		User manager = saveUser("role-reject-manager@example.com", UserRole.MANAGER);
		User elder = saveUser("role-reject-elder@example.com", UserRole.USER);
		User leader = saveUser("role-reject-leader@example.com", UserRole.USER);
		User member = saveUser("role-reject-member@example.com", UserRole.USER);
		User target = saveUser("role-reject-target@example.com", UserRole.USER);
		User outsiderManager = saveUser("role-reject-outsider-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"31캠",
			"분당",
			"분당 31캠퍼스"
		));
		CampusMembershipResult elderMembership = campusService.joinCampus(new JoinCampusCommand(elder.id(), campus.inviteCode()));
		CampusMembershipResult leaderMembership = campusService.joinCampus(new JoinCampusCommand(leader.id(), campus.inviteCode()));
		CampusMembershipResult memberMembership = campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		CampusMembershipResult targetMembership = campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));
		CampusMember ministerMembership = campusMemberRepository.findByCampusIdAndUserId(campus.campusId(), manager.id())
			.orElseThrow();
		updateCampusRole(elderMembership.membershipId(), CampusRole.ELDER);
		updateCampusRole(leaderMembership.membershipId(), CampusRole.CAMPUS_LEADER);

		assertThatThrownBy(() -> campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), ministerMembership.id(), elder.id(), CampusRole.MEMBER
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("상위 캠퍼스 역할은 변경할 수 없습니다.");
		assertThatThrownBy(() -> campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), targetMembership.membershipId(), elder.id(), CampusRole.MINISTER
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("상위 캠퍼스 역할은 변경할 수 없습니다.");
		assertThatThrownBy(() -> campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), elderMembership.membershipId(), leader.id(), CampusRole.MEMBER
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("상위 캠퍼스 역할은 변경할 수 없습니다.");
		assertThatThrownBy(() -> campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), targetMembership.membershipId(), leader.id(), CampusRole.ELDER
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("상위 캠퍼스 역할은 변경할 수 없습니다.");
		assertThatThrownBy(() -> campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), ministerMembership.id(), leader.id(), CampusRole.MEMBER
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("상위 캠퍼스 역할은 변경할 수 없습니다.");
		assertThatThrownBy(() -> campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), targetMembership.membershipId(), leader.id(), CampusRole.MINISTER
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("상위 캠퍼스 역할은 변경할 수 없습니다.");
		assertThatThrownBy(() -> campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), targetMembership.membershipId(), member.id(), CampusRole.MEMBER
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("캠퍼스 역할 변경 권한이 없습니다.");
		assertThatThrownBy(() -> campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), targetMembership.membershipId(), outsiderManager.id(), CampusRole.MEMBER
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("캠퍼스 역할 변경 권한이 없습니다.");

		assertThat(campusMemberRepository.findById(memberMembership.membershipId())).isPresent();
	}

	@Test
	void changeCampusRole_allows_service_admin_and_last_manager_role_downgrade() {
		User manager = saveUser("role-admin-manager@example.com", UserRole.MANAGER);
		User admin = saveUser("role-admin@example.com", UserRole.ADMIN);
		User target = saveUser("role-admin-target@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"32캠",
			"분당",
			"분당 32캠퍼스"
		));
		CampusMembershipResult targetMembership = campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));
		updateCampusRole(targetMembership.membershipId(), CampusRole.MINISTER);

		assertThat(campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), targetMembership.membershipId(), admin.id(), CampusRole.ELDER
		)).campusRole()).isEqualTo("ELDER");
		assertThat(campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), targetMembership.membershipId(), admin.id(), CampusRole.MINISTER
		)).campusRole()).isEqualTo("MINISTER");
		assertThat(campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), targetMembership.membershipId(), admin.id(), CampusRole.CAMPUS_LEADER
		)).campusRole()).isEqualTo("CAMPUS_LEADER");
		assertThat(campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), targetMembership.membershipId(), admin.id(), CampusRole.MEMBER
		)).campusRole()).isEqualTo("MEMBER");

		CampusMember creatorMembership = campusMemberRepository.findByCampusIdAndUserId(campus.campusId(), manager.id()).orElseThrow();
		assertThat(campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campus.campusId(), creatorMembership.id(), admin.id(), CampusRole.MEMBER
		)).campusRole()).isEqualTo("MEMBER");
	}

	@Test
	void assignCoffeeDuty_keeps_multiple_active_assignments_and_is_idempotent_per_user() {
		User manager = saveUser("coffee-manager@example.com", UserRole.MANAGER);
		User elder = saveUser("coffee-elder@example.com", UserRole.USER);
		User first = saveUser("coffee-first@example.com", UserRole.USER);
		User second = saveUser("coffee-second@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"33캠",
			"분당",
			"분당 33캠퍼스"
		));
		CampusMembershipResult elderMembership = campusService.joinCampus(new JoinCampusCommand(elder.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(first.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(second.id(), campus.inviteCode()));
		updateCampusRole(elderMembership.membershipId(), CampusRole.ELDER);

		DutyAssignmentResult firstAssignment = campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), elder.id(), first.id()
		));
		DutyAssignmentResult secondAssignment = campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), elder.id(), second.id()
		));
		DutyAssignmentResult repeatedFirstAssignment = campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), elder.id(), first.id()
		));

		assertThat(firstAssignment.dutyType()).isEqualTo(DutyType.COFFEE.name());
		assertThat(secondAssignment.userId()).isEqualTo(second.id());
		assertThat(repeatedFirstAssignment.assignmentId()).isEqualTo(firstAssignment.assignmentId());
		assertThat(campusService.getDutyAssignments(campus.campusId(), elder.id()))
			.extracting(DutyAssignmentResult::userId)
			.containsExactly(first.id(), second.id());

		campusService.revokeCoffeeDuty(campus.campusId(), secondAssignment.assignmentId(), elder.id());

		assertThat(campusService.getDutyAssignments(campus.campusId(), elder.id()))
			.extracting(DutyAssignmentResult::userId)
			.containsExactly(first.id());
	}

	@Test
	void assignCoffeeDuty_requires_non_member_campus_role_or_admin_and_existing_target_membership() {
		User manager = saveUser("coffee-permission-manager@example.com", UserRole.MANAGER);
		User member = saveUser("coffee-permission-member@example.com", UserRole.USER);
		User target = saveUser("coffee-permission-target@example.com", UserRole.USER);
		User outsiderManager = saveUser("coffee-permission-outsider-manager@example.com", UserRole.MANAGER);
		User admin = saveUser("coffee-permission-admin@example.com", UserRole.ADMIN);
		User outsider = saveUser("coffee-permission-outsider@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"34캠",
			"분당",
			"분당 34캠퍼스"
		));
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));

		assertThatThrownBy(() -> campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), member.id(), target.id()
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("커피 담당자 관리 권한이 없습니다.");
		assertThatThrownBy(() -> campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), outsiderManager.id(), target.id()
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("커피 담당자 관리 권한이 없습니다.");
		assertThatThrownBy(() -> campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), admin.id(), outsider.id()
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("커피 담당자로 지정할 캠퍼스 멤버를 찾을 수 없습니다.");

		assertThat(campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), admin.id(), target.id()
		)).userId()).isEqualTo(target.id());
	}

	@Test
	void getMyCoffeeDuty_returns_active_true_only_for_current_active_coffee_assignee() {
		User manager = saveUser("coffee-me-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("coffee-me-duty@example.com", UserRole.USER);
		User member = saveUser("coffee-me-member@example.com", UserRole.USER);
		User outsider = saveUser("coffee-me-outsider@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"35캠",
			"분당",
			"분당 35캠퍼스"
		));
		campusService.joinCampus(new JoinCampusCommand(duty.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));

		MyDutyAssignmentResult active = campusService.getMyCoffeeDutyAssignment(campus.campusId(), duty.id());
		MyDutyAssignmentResult inactive = campusService.getMyCoffeeDutyAssignment(campus.campusId(), member.id());

		assertThat(active.userId()).isEqualTo(duty.id());
		assertThat(active.campusId()).isEqualTo(campus.campusId());
		assertThat(active.dutyType()).isEqualTo(DutyType.COFFEE.name());
		assertThat(active.active()).isTrue();
		assertThat(inactive.userId()).isEqualTo(member.id());
		assertThat(inactive.campusId()).isEqualTo(campus.campusId());
		assertThat(inactive.dutyType()).isEqualTo(DutyType.COFFEE.name());
		assertThat(inactive.active()).isFalse();
		assertThatThrownBy(() -> campusService.getMyCoffeeDutyAssignment(campus.campusId(), outsider.id()))
			.isInstanceOf(BusinessException.class)
			.hasMessage("캠퍼스 조회 권한이 없습니다.");
	}

	@Test
	void revokeCoffeeAndMealDuty_rejects_unpaid_charges_on_owned_inactive_accounts() {
		User manager = saveUser("duty-revoke-manager@example.com", UserRole.MANAGER);
		User coffeeDuty = saveUser("duty-revoke-coffee@example.com", UserRole.USER);
		User mealDuty = saveUser("duty-revoke-meal@example.com", UserRole.USER);
		User target = saveUser("duty-revoke-target@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "200담당해제캠", "분당", "담당 해제 미납 검증"
		));
		campusService.joinCampus(new JoinCampusCommand(coffeeDuty.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(mealDuty.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));
		DutyAssignmentResult coffeeAssignment = campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), manager.id(), coffeeDuty.id()
		));
		DutyAssignmentResult mealAssignment = campusService.assignMealDuty(new AssignMealDutyCommand(
			campus.campusId(), manager.id(), mealDuty.id()
		));

		PaymentAccount coffeeAccount = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.campusId(), PaymentCategory.COFFEE, "비활성 커피 계좌", "하나은행", "200-COFFEE", "커피담당", coffeeDuty.id()
		));
		PaymentAccount mealAccount = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.campusId(), PaymentCategory.MEAL, "비활성 밥 계좌", "국민은행", "200-MEAL", "밥담당", mealDuty.id()
		));
		coffeeAccount.deactivate();
		mealAccount.deactivate();
		ChargeItem coffeeUnpaid = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(), target.id(), PaymentCategory.COFFEE, coffeeAccount.id(), coffeeAccount.bankName(),
			coffeeAccount.accountNumber(), coffeeAccount.accountHolder(), ChargeSourceType.POLL_RESPONSE, 20001L,
			"커피 미납", "커피", 1800, null
		));
		ChargeItem coffeeWaived = ChargeItem.create(
			campus.campusId(), target.id(), PaymentCategory.COFFEE, coffeeAccount.id(), coffeeAccount.bankName(),
			coffeeAccount.accountNumber(), coffeeAccount.accountHolder(), ChargeSourceType.POLL_RESPONSE, 20003L,
			"커피 면제", "커피", 2000, null
		);
		coffeeWaived.waive();
		chargeItemRepository.saveAndFlush(coffeeWaived);
		ChargeItem coffeeCanceled = ChargeItem.create(
			campus.campusId(), target.id(), PaymentCategory.COFFEE, coffeeAccount.id(), coffeeAccount.bankName(),
			coffeeAccount.accountNumber(), coffeeAccount.accountHolder(), ChargeSourceType.POLL_RESPONSE, 20004L,
			"커피 취소", "커피", 2500, null
		);
		coffeeCanceled.cancel();
		chargeItemRepository.saveAndFlush(coffeeCanceled);
		ChargeItem mealUnpaid = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(), target.id(), PaymentCategory.MEAL, mealAccount.id(), mealAccount.bankName(),
			mealAccount.accountNumber(), mealAccount.accountHolder(), ChargeSourceType.POLL_RESPONSE, 20002L,
			"밥 미납", "밥", 7000, null
		));

		assertThatThrownBy(() -> campusService.revokeCoffeeDuty(
			campus.campusId(), coffeeAssignment.assignmentId(), manager.id()
		))
			.isInstanceOf(BusinessException.class)
			.hasMessage("소유한 커피 계좌에 미납 청구가 있어 담당자를 해제할 수 없습니다.");
		assertThatThrownBy(() -> campusService.revokeMealDuty(
			campus.campusId(), mealAssignment.assignmentId(), manager.id()
		))
			.isInstanceOf(BusinessException.class)
			.hasMessage("소유한 밥 계좌에 미납 청구가 있어 담당자를 해제할 수 없습니다.");

		coffeeUnpaid.markPaid();
		mealUnpaid.cancel();
		chargeItemRepository.saveAndFlush(coffeeUnpaid);
		chargeItemRepository.saveAndFlush(mealUnpaid);
		campusService.revokeCoffeeDuty(campus.campusId(), coffeeAssignment.assignmentId(), manager.id());
		campusService.revokeMealDuty(campus.campusId(), mealAssignment.assignmentId(), manager.id());
		assertThat(campusService.getDutyAssignments(campus.campusId(), manager.id()))
			.extracting(DutyAssignmentResult::assignmentId)
			.doesNotContain(coffeeAssignment.assignmentId(), mealAssignment.assignmentId());
	}

	@Test
	void revokeCoffeeDuty_skips_charge_query_when_assignee_has_no_owned_account() {
		User manager = saveUser("duty-revoke-empty-scope-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("duty-revoke-empty-scope-duty@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "200담당해제빈범위캠", "분당", "담당 해제 빈 계좌 범위 검증"
		));
		campusService.joinCampus(new JoinCampusCommand(duty.id(), campus.inviteCode()));
		DutyAssignmentResult assignment = campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), manager.id(), duty.id()
		));
		clearInvocations(chargeItemRepository);

		campusService.revokeCoffeeDuty(campus.campusId(), assignment.assignmentId(), manager.id());

		verify(chargeItemRepository, never()).findByCampusIdAndPaymentCategoryAndStatus(
			campus.campusId(), PaymentCategory.COFFEE, ChargeStatus.UNPAID
		);
		verify(chargeItemRepository, never())
			.findByCampusIdAndPaymentCategoryAndStatusAndPaymentAccountIdInOrderByIdAsc(
				campus.campusId(), PaymentCategory.COFFEE, ChargeStatus.UNPAID, anySet()
			);
	}

	@Test
	void revokeCoffeeDuty_queries_unpaid_charges_only_for_owned_account_ids() {
		User manager = saveUser("duty-revoke-owned-scope-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("duty-revoke-owned-scope-duty@example.com", UserRole.USER);
		User target = saveUser("duty-revoke-owned-scope-target@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "200담당해제소유범위캠", "분당", "담당 해제 소유 계좌 범위 검증"
		));
		campusService.joinCampus(new JoinCampusCommand(duty.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));
		DutyAssignmentResult assignment = campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), manager.id(), duty.id()
		));
		PaymentAccount ownedAccount = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.campusId(), PaymentCategory.COFFEE, "담당자 계좌", "하나은행", "200-OWNED-SCOPE",
			"담당자", duty.id()
		));
		PaymentAccount otherAccount = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.campusId(), PaymentCategory.COFFEE, "다른 계좌", "국민은행", "200-OTHER-SCOPE",
			"관리자", manager.id()
		));
		chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(), target.id(), PaymentCategory.COFFEE, otherAccount.id(), otherAccount.bankName(),
			otherAccount.accountNumber(), otherAccount.accountHolder(), ChargeSourceType.POLL_RESPONSE, 20005L,
			"다른 담당자 미납", "범위 밖 미납", 1900, null
		));
		clearInvocations(chargeItemRepository);

		campusService.revokeCoffeeDuty(campus.campusId(), assignment.assignmentId(), manager.id());

		verify(chargeItemRepository, never()).findByCampusIdAndPaymentCategoryAndStatus(
			campus.campusId(), PaymentCategory.COFFEE, ChargeStatus.UNPAID
		);
		verify(chargeItemRepository)
			.findByCampusIdAndPaymentCategoryAndStatusAndPaymentAccountIdInOrderByIdAsc(
				campus.campusId(), PaymentCategory.COFFEE, ChargeStatus.UNPAID, Set.of(ownedAccount.id())
			);
	}

	private void updateCampusRole(Long membershipId, CampusRole campusRole) {
		CampusMember member = campusMemberRepository.findById(membershipId).orElseThrow();
		ReflectionTestUtils.setField(member, "campusRole", campusRole);
		campusMemberRepository.saveAndFlush(member);
	}

	private User saveUser(String email, UserRole role) {
		User user = User.create("서비스테스트", email, "dummy-password-hash");
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}

	@TestConfiguration
	static class CampusServiceTestConfig {

		@Bean
		@Primary
		InviteCodeGenerator inviteCodeGenerator() {
			return new DeterministicInviteCodeGenerator();
		}
	}

	static class DeterministicInviteCodeGenerator extends InviteCodeGenerator {

		private final AtomicInteger count = new AtomicInteger();

		void reset() {
			count.set(0);
		}

		@Override
		public String generate() {
			int current = count.getAndIncrement();
			if (current < 2) {
				return "FL-DUPLICATE";
			}
			return "FL-UNIQUE-" + current;
		}
	}
}
