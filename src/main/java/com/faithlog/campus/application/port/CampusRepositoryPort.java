package com.faithlog.campus.application.port;

import com.faithlog.campus.domain.Campus;
import java.util.Optional;

public interface CampusRepositoryPort {

	Campus save(Campus campus);

	Optional<Campus> findById(Long campusId);

	Optional<Campus> findByInviteCode(String inviteCode);

	boolean existsByInviteCode(String inviteCode);
}
