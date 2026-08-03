package com.faithlog.user.domain.entity;

import com.faithlog.user.domain.type.YearlyRecapArchiveFactType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "yearly_recap_archive_coverage",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_yearly_recap_archive_coverage_type",
		columnNames = "fact_type"
	)
)
public class YearlyRecapArchiveCoverage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Enumerated(EnumType.STRING)
	@Column(name = "fact_type", nullable = false, length = 30)
	private YearlyRecapArchiveFactType factType;
	@Column(name = "complete_from_year", nullable = false)
	private int completeFromYear;

	protected YearlyRecapArchiveCoverage() {
	}
}
