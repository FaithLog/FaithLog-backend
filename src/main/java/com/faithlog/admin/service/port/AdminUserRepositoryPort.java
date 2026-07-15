package com.faithlog.admin.service.port;

import com.faithlog.admin.service.query.AdminUserSearchCriteria;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserRepositoryPort {

	Optional<User> findAdminUserById(Long userId);

	List<User> findAdminUsersByIdsForUpdate(Collection<Long> userIds);

	List<User> findActiveAdminUsersForUpdate(UserRole role);

	Page<User> searchAdminUsers(AdminUserSearchCriteria criteria, Pageable pageable);

	long countByRoleAndIsActiveTrue(UserRole role);
}
