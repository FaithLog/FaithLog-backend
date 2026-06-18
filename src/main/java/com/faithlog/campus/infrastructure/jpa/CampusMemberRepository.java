package com.faithlog.campus.infrastructure.jpa;

import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusMemberRepository extends JpaRepository<CampusMember, Long> {

	boolean existsByCampusIdAndUserId(Long campusId, Long userId);

	Optional<CampusMember> findByCampusIdAndUserId(Long campusId, Long userId);

	List<CampusMember> findByUserIdAndStatusOrderByIdDesc(Long userId, CampusMemberStatus status);
}
