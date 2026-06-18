package com.faithlog.campus.application.port;

import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusMemberStatus;
import java.util.List;
import java.util.Optional;

public interface CampusMemberRepositoryPort {

	CampusMember save(CampusMember campusMember);

	boolean existsByCampusIdAndUserId(Long campusId, Long userId);

	Optional<CampusMember> findByCampusIdAndUserId(Long campusId, Long userId);

	Optional<CampusMember> findByCampusIdAndId(Long campusId, Long id);

	List<CampusMember> findByUserIdAndStatusOrderByIdDesc(Long userId, CampusMemberStatus status);
}
