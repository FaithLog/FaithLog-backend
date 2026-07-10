package com.faithlog.campus.service.port;

import com.faithlog.campus.domain.entity.Campus;
import java.util.Optional;

public interface CampusRepositoryPort {

	Campus save(Campus campus);

	Optional<Campus> findById(Long campusId);

	Optional<Campus> findByIdForUpdate(Long campusId);

	Optional<Campus> findByInviteCode(String inviteCode);

	boolean existsByInviteCode(String inviteCode);
}
