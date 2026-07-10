package com.faithlog.prayer.infrastructure.repository;

import com.faithlog.prayer.domain.entity.PrayerGroupMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrayerGroupMemberRepository extends JpaRepository<PrayerGroupMember, Long> {

	List<PrayerGroupMember> findByGroupIdOrderByIdAsc(Long groupId);

	List<PrayerGroupMember> findByGroupIdAndIsActiveTrueOrderByIdAsc(Long groupId);

	List<PrayerGroupMember> findByGroupIdInAndIsActiveTrueOrderByIdAsc(Collection<Long> groupIds);

	Optional<PrayerGroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

	boolean existsByGroupIdInAndUserIdAndIsActiveTrue(Collection<Long> groupIds, Long userId);
}
