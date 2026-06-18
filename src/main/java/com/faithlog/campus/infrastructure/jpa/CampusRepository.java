package com.faithlog.campus.infrastructure.jpa;

import com.faithlog.campus.domain.Campus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusRepository extends JpaRepository<Campus, Long> {

	Optional<Campus> findByInviteCode(String inviteCode);

	boolean existsByInviteCode(String inviteCode);
}
