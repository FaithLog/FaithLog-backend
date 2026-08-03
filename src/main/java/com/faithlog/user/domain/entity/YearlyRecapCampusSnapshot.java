package com.faithlog.user.domain.entity;

import com.faithlog.user.service.result.CampusJourneyResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

@Entity
@Table(
	name = "yearly_recap_campuses",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_yearly_recap_campuses_snapshot_campus",
		columnNames = {"yearly_recap_snapshot_id", "campus_id"}
	)
)
public class YearlyRecapCampusSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "yearly_recap_snapshot_id", nullable = false)
	private Long yearlyRecapSnapshotId;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "campus_name", nullable = false, length = 100)
	private String campusName;

	@Column(name = "joined_date", nullable = false)
	private LocalDate joinedDate;

	@Column(name = "joined_during_recap_year", nullable = false)
	private boolean joinedDuringRecapYear;

	protected YearlyRecapCampusSnapshot() {
	}

	private YearlyRecapCampusSnapshot(Long snapshotId, CampusJourneyResult campus) {
		this.yearlyRecapSnapshotId = snapshotId;
		this.campusId = campus.campusId();
		this.campusName = campus.campusName();
		this.joinedDate = campus.joinedDate();
		this.joinedDuringRecapYear = campus.joinedDuringRecapYear();
	}

	public static YearlyRecapCampusSnapshot create(Long snapshotId, CampusJourneyResult campus) {
		return new YearlyRecapCampusSnapshot(snapshotId, campus);
	}

	public CampusJourneyResult toResult() {
		return new CampusJourneyResult(campusId, campusName, joinedDate, joinedDuringRecapYear);
	}
}
