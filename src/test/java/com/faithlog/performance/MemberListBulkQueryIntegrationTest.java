package com.faithlog.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.admin.service.AdminManagementService;
import com.faithlog.admin.service.query.AdminUserSearchCriteria;
import com.faithlog.admin.service.result.AdminUserCampusResult;
import com.faithlog.admin.service.result.AdminUserResult;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.result.AdminCampusMemberResult;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class MemberListBulkQueryIntegrationTest {

	@Autowired
	private AdminManagementService adminManagementService;

	@Autowired
	private CampusService campusService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Test
	void admin_user_page_bulk_loads_membership_and_campus_projection_without_n_plus_one() {
		User admin = saveUser("issue195-admin@example.com", "Issue195 Admin", UserRole.ADMIN);
		Campus firstCampus = campusRepository.saveAndFlush(Campus.create(
			"Issue195 Campus A", "Seoul", "bulk projection A", "ISSUE195-A"));
		Campus secondCampus = campusRepository.saveAndFlush(Campus.create(
			"Issue195 Campus B", "Busan", "bulk projection B", "ISSUE195-B"));
		List<User> targets = new ArrayList<>();
		for (int index = 0; index < 4; index++) {
			User target = saveUser(
				"issue195-target-" + index + "@example.com",
				"Issue195 Bulk Target",
				UserRole.USER
			);
			targets.add(target);
			campusMemberRepository.saveAndFlush(CampusMember.createMember(firstCampus.id(), target.id()));
			campusMemberRepository.saveAndFlush(CampusMember.createMember(secondCampus.id(), target.id()));
		}
		campusMemberRepository.flush();
		Statistics statistics = resetStatistics();

		Page<AdminUserResult> page = adminManagementService.searchUsers(
			admin.id(),
			new AdminUserSearchCriteria("Issue195 Bulk Target", null, null, UserRole.USER),
			PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "id"))
		);

		assertThat(page.getContent())
			.extracting(AdminUserResult::userId)
			.containsExactlyElementsOf(targets.stream().map(User::id).toList());
		assertThat(page.getNumber()).isZero();
		assertThat(page.getSize()).isEqualTo(100);
		assertThat(page.getTotalElements()).isEqualTo(4);
		assertThat(page.getTotalPages()).isEqualTo(1);
		assertThat(page.getContent())
			.allSatisfy(result -> {
				assertThat(result.campusCount()).isEqualTo(2);
				assertThat(result.campuses())
					.extracting(AdminUserCampusResult::campusId)
					.containsExactly(firstCampus.id(), secondCampus.id());
			});
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
	}

	@Test
	void campus_member_list_bulk_loads_users_without_per_member_lookup() {
		User admin = saveUser("issue195-campus-admin@example.com", "Issue195 Admin", UserRole.ADMIN);
		Campus campus = campusRepository.saveAndFlush(Campus.create(
			"Issue195 Member Campus", "Seoul", "bulk users", "ISSUE195-MEMBERS"));
		List<CampusMember> activeMembers = new ArrayList<>();
		for (int index = 0; index < 4; index++) {
			User member = saveUser(
				"issue195-member-" + index + "@example.com",
				"Issue195 Member " + index,
				UserRole.USER
			);
			activeMembers.add(campusMemberRepository.saveAndFlush(CampusMember.createMember(campus.id(), member.id())));
		}
		User archivedUser = saveUser("issue195-archived@example.com", "Issue195 Archived", UserRole.USER);
		CampusMember archivedMember = CampusMember.createMember(campus.id(), archivedUser.id());
		archivedMember.deactivate();
		campusMemberRepository.saveAndFlush(archivedMember);
		Statistics statistics = resetStatistics();

		List<AdminCampusMemberResult> results = campusService.getCampusMembers(campus.id(), admin.id());

		assertThat(results)
			.extracting(AdminCampusMemberResult::membershipId)
			.containsExactlyElementsOf(activeMembers.stream().map(CampusMember::id).toList());
		assertThat(results)
			.extracting(AdminCampusMemberResult::status)
			.containsOnly("ACTIVE");
		assertThat(results)
			.extracting(AdminCampusMemberResult::name)
			.containsExactly("Issue195 Member 0", "Issue195 Member 1", "Issue195 Member 2", "Issue195 Member 3");
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
	}

	private Statistics resetStatistics() {
		entityManager.flush();
		entityManager.clear();
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		return statistics;
	}

	private User saveUser(String email, String name, UserRole role) {
		User user = User.create(name, email, "dummy-password-hash");
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.save(user);
	}
}
