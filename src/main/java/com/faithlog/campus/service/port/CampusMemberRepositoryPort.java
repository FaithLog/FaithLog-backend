package com.faithlog.campus.service.port;

import com.faithlog.campus.service.result.CampusMembershipRow;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import java.util.List;
import java.util.Optional;

public interface CampusMemberRepositoryPort {

	CampusMember save(CampusMember campusMember);

	boolean existsByCampusIdAndUserId(Long campusId, Long userId);

	Optional<CampusMember> findByCampusIdAndUserId(Long campusId, Long userId);

	Optional<CampusMember> findByCampusIdAndId(Long campusId, Long id);

	List<CampusMember> findByCampusIdAndStatusOrderByIdAsc(Long campusId, CampusMemberStatus status);

	long countByCampusIdAndStatus(Long campusId, CampusMemberStatus status);

	List<CampusMember> findByUserIdAndStatusOrderByIdDesc(Long userId, CampusMemberStatus status);

	List<CampusMembershipRow> findMembershipRowsByUserIdAndStatusOrderByIdDesc(Long userId, CampusMemberStatus status);

	List<CampusMember> findByUserIdOrderByIdAsc(Long userId);
}
