package com.faithlog.campus.infrastructure.jpa;

import com.faithlog.campus.application.port.CampusMemberRepositoryPort;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusMemberRepository extends JpaRepository<CampusMember, Long>, CampusMemberRepositoryPort {

	boolean existsByCampusIdAndUserId(Long campusId, Long userId);

	Optional<CampusMember> findByCampusIdAndUserId(Long campusId, Long userId);

	Optional<CampusMember> findByCampusIdAndId(Long campusId, Long id);

	List<CampusMember> findByCampusIdAndStatusOrderByIdAsc(Long campusId, CampusMemberStatus status);

	List<CampusMember> findByUserIdAndStatusOrderByIdDesc(Long userId, CampusMemberStatus status);
}
