package com.faithlog.admin.service.port;

import com.faithlog.admin.service.query.AdminUserSearchCriteria;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserRepositoryPort {

	Optional<User> findAdminUserById(Long userId);

	Page<User> searchAdminUsers(AdminUserSearchCriteria criteria, Pageable pageable);

	long countByRoleAndIsActiveTrue(UserRole role);
}
