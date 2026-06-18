package com.faithlog.campus.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusMemberStatus;
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
			manager.role().name(),
			"20캠",
			"분당",
			"분당 20캠퍼스"
		));
		CampusCreateResult second = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			manager.role().name(),
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
			manager.role().name(),
			"22캠",
			"분당",
			"분당 22캠퍼스"
		));
		CampusCreateResult inactiveCampus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			manager.role().name(),
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
