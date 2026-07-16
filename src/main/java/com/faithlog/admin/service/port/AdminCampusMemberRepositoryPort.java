package com.faithlog.admin.service.port;

import com.faithlog.admin.service.result.AdminUserCampusRow;
import com.faithlog.campus.domain.entity.CampusMember;
import java.util.Collection;
import java.util.List;

public interface AdminCampusMemberRepositoryPort {

	List<CampusMember> findByUserIdOrderByIdAsc(Long userId);

	List<AdminUserCampusRow> findAdminUserCampusRowsByUserIds(Collection<Long> userIds);
}
