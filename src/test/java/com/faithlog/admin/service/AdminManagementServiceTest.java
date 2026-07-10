package com.faithlog.admin.service;

import com.faithlog.admin.service.command.AddCampusMemberCommand;
import com.faithlog.admin.service.command.ChangeUserRoleCommand;
import com.faithlog.admin.service.query.AdminUserSearchCriteria;
import com.faithlog.admin.service.result.AdminUserResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.result.AdminCampusMemberResult;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminManagementServiceTest {

	@Autowired
	private AdminManagementService adminManagementService;

	@Autowired
	private CampusService campusService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Test
	void searchUsers_and_getUser_include_membership_summaries_for_service_admin_only() {
		User admin = saveUser("service-admin-search-admin@example.com", UserRole.ADMIN, "서비스관리자");
		User manager = saveUser("service-admin-search-manager@example.com", UserRole.MANAGER, "서비스매니저");
		User user = saveUser("service-admin-search-user@example.com", UserRole.USER, "검색회원");
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"검색캠",
			"분당",
			"분당 검색캠"
		));
		adminManagementService.addCampusMember(new AddCampusMemberCommand(admin.id(), campus.campusId(), user.id()));

		assertThatThrownBy(() -> adminManagementService.searchUsers(
			user.id(),
			new AdminUserSearchCriteria(null, null, null, null),
			PageRequest.of(0, 20)
		))
			.isInstanceOf(BusinessException.class)
			.hasMessage("서비스 관리자 권한이 없습니다.");

		AdminUserResult detail = adminManagementService.getUser(admin.id(), user.id());

		assertThat(detail.userId()).isEqualTo(user.id());
		assertThat(detail.campusCount()).isEqualTo(1);
		assertThat(detail.campuses().getFirst().campusName()).isEqualTo("검색캠");
		assertThat(adminManagementService.searchUsers(
			admin.id(),
			new AdminUserSearchCriteria("검색", "search-user", user.id(), UserRole.USER),
			PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "email"))
		)).hasSize(1);
	}

	@Test
	void changeUserRole_changes_only_global_role_and_blocks_last_active_admin_demotion() {
		User admin = saveUser("service-admin-role-admin@example.com", UserRole.ADMIN, "서비스관리자");
		User secondAdmin = saveUser("service-admin-role-second@example.com", UserRole.ADMIN, "두번째관리자");
		User target = saveUser("service-admin-role-target@example.com", UserRole.USER, "대상회원");
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			secondAdmin.id(),
			"역할캠",
			"분당",
			"분당 역할캠"
		));
		AdminCampusMemberResult membership = adminManagementService.addCampusMember(
			new AddCampusMemberCommand(admin.id(), campus.campusId(), target.id())
		);
		CampusMember campusMember = campusMemberRepository.findById(membership.membershipId()).orElseThrow();
		ReflectionTestUtils.setField(campusMember, "campusRole", CampusRole.ELDER);

		AdminUserResult changed = adminManagementService.changeUserRole(new ChangeUserRoleCommand(
			admin.id(),
			target.id(),
			UserRole.MANAGER
		));

		assertThat(changed.role()).isEqualTo("MANAGER");
		assertThat(campusMemberRepository.findById(membership.membershipId()).orElseThrow().campusRole())
			.isEqualTo(CampusRole.ELDER);

		adminManagementService.changeUserRole(new ChangeUserRoleCommand(admin.id(), admin.id(), UserRole.USER));

		assertThatThrownBy(() -> adminManagementService.changeUserRole(new ChangeUserRoleCommand(
			secondAdmin.id(),
			secondAdmin.id(),
			UserRole.USER
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("마지막 서비스 관리자는 강등할 수 없습니다.");
	}

	@Test
	void addCampusMember_creates_rejects_active_duplicate_and_reactivates_inactive_member() {
		User admin = saveUser("service-admin-add-admin@example.com", UserRole.ADMIN, "서비스관리자");
		User manager = saveUser("service-admin-add-manager@example.com", UserRole.MANAGER, "캠퍼스관리자");
		User target = saveUser("service-admin-add-target@example.com", UserRole.USER, "직접추가");
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"추가캠",
			"분당",
			"분당 추가캠"
		));

		AdminCampusMemberResult added = adminManagementService.addCampusMember(new AddCampusMemberCommand(
			admin.id(),
			campus.campusId(),
			target.id()
		));

		assertThat(added.userId()).isEqualTo(target.id());
		assertThat(added.campusRole()).isEqualTo("MEMBER");
		assertThat(added.status()).isEqualTo("ACTIVE");
		assertThatThrownBy(() -> adminManagementService.addCampusMember(new AddCampusMemberCommand(
			admin.id(),
			campus.campusId(),
			target.id()
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("이미 가입된 캠퍼스입니다.");

		campusMemberRepository.findById(added.membershipId()).orElseThrow().deactivate();

		AdminCampusMemberResult reactivated = adminManagementService.addCampusMember(new AddCampusMemberCommand(
			admin.id(),
			campus.campusId(),
			target.id()
		));

		assertThat(reactivated.membershipId()).isEqualTo(added.membershipId());
		assertThat(campusMemberRepository.findById(added.membershipId()).orElseThrow().status())
			.isEqualTo(CampusMemberStatus.ACTIVE);
	}

	private User saveUser(String email, UserRole role, String name) {
		User user = User.create(name, email, "dummy-password-hash");
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}
}
