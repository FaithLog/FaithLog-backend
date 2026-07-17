package com.faithlog.admin.service;

import com.faithlog.admin.service.command.AddCampusMemberCommand;
import com.faithlog.admin.service.command.ChangeUserRoleCommand;
import com.faithlog.admin.service.query.AdminCampusSearchCriteria;
import com.faithlog.admin.service.query.AdminUserSearchCriteria;
import com.faithlog.admin.service.result.AdminCampusResult;
import com.faithlog.admin.service.result.AdminUserResult;
import com.faithlog.campus.service.result.AdminCampusMemberResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AdminManagementService {

	private final AdminUserManagementService adminUserManagementService;
	private final AdminCampusManagementService adminCampusManagementService;

	public AdminManagementService(
		AdminUserManagementService adminUserManagementService,
		AdminCampusManagementService adminCampusManagementService
	) {
		this.adminUserManagementService = adminUserManagementService;
		this.adminCampusManagementService = adminCampusManagementService;
	}

	public Page<AdminUserResult> searchUsers(Long requesterId, AdminUserSearchCriteria criteria, Pageable pageable) {
		return adminUserManagementService.searchUsers(requesterId, criteria, pageable);
	}

	public AdminUserResult getUser(Long requesterId, Long userId) {
		return adminUserManagementService.getUser(requesterId, userId);
	}

	public AdminUserResult changeUserRole(ChangeUserRoleCommand command) {
		return adminUserManagementService.changeUserRole(command);
	}

	public Page<AdminCampusResult> searchCampuses(Long requesterId, AdminCampusSearchCriteria criteria, Pageable pageable) {
		return adminCampusManagementService.searchCampuses(requesterId, criteria, pageable);
	}

	public AdminCampusMemberResult addCampusMember(AddCampusMemberCommand command) {
		return adminCampusManagementService.addCampusMember(command);
	}
}
