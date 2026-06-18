package com.faithlog.campus.infrastructure.jpa;

import com.faithlog.campus.application.port.CampusRepositoryPort;
import com.faithlog.campus.domain.Campus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusRepository extends JpaRepository<Campus, Long>, CampusRepositoryPort {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select campus from Campus campus where campus.id = :campusId")
	Optional<Campus> findByIdForUpdate(@Param("campusId") Long campusId);

	Optional<Campus> findByInviteCode(String inviteCode);

	boolean existsByInviteCode(String inviteCode);
}
