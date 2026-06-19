package com.faithlog.admin.application.port;

import com.faithlog.campus.domain.CampusMember;
import java.util.List;

public interface AdminCampusMemberRepositoryPort {

	List<CampusMember> findByUserIdOrderByIdAsc(Long userId);
}
