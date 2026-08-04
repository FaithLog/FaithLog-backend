package com.faithlog.user.infrastructure.repository;

import com.faithlog.user.domain.entity.YearlyRecapCampusSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface YearlyRecapCampusSnapshotRepository extends JpaRepository<YearlyRecapCampusSnapshot, Long> {

	List<YearlyRecapCampusSnapshot> findByYearlyRecapSnapshotIdOrderByCampusIdAsc(Long snapshotId);
}
