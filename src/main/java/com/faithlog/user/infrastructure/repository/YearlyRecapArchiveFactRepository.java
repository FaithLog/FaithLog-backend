package com.faithlog.user.infrastructure.repository;

import com.faithlog.user.domain.entity.YearlyRecapArchiveFact;
import com.faithlog.user.domain.type.YearlyRecapArchiveFactType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface YearlyRecapArchiveFactRepository extends JpaRepository<YearlyRecapArchiveFact, Long> {

	@Query("""
		select fact.sourceId from YearlyRecapArchiveFact fact
		where fact.factType = :factType and fact.sourceId in :sourceIds
		""")
	List<Long> findExistingSourceIds(
		@Param("factType") YearlyRecapArchiveFactType factType,
		@Param("sourceIds") Collection<Long> sourceIds
	);
}
