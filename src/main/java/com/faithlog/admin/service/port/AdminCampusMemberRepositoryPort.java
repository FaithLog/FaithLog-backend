package com.faithlog.admin.service.port;

import com.faithlog.campus.domain.CampusMember;
import java.util.List;

public interface AdminCampusMemberRepositoryPort {

	List<CampusMember> findByUserIdOrderByIdAsc(Long userId);
}
