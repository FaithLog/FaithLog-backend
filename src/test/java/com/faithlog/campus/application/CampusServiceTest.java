package com.faithlog.campus.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusMemberStatus;
import com.faithlog.campus.domain.CampusRole;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.campus.infrastructure.jpa.CampusMemberRepository;
import com.faithlog.campus.infrastructure.jpa.CampusRepository;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampusServiceTest {

	@Autowired
	private CampusService campusService;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private UserRepository userRepository;

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
