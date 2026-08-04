package com.faithlog.user.domain.entity;

import com.faithlog.user.domain.type.YearlyRecapArchiveFactType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
	name = "yearly_recap_archive_facts",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_yearly_recap_archive_facts_type_source",
		columnNames = {"fact_type", "source_id"}
	)
)
public class YearlyRecapArchiveFact {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Enumerated(EnumType.STRING)
	@Column(name = "fact_type", nullable = false, length = 30)
	private YearlyRecapArchiveFactType factType;
	@Column(name = "source_id", nullable = false)
	private Long sourceId;
	@Column(name = "user_id", nullable = false)
	private Long userId;
	@Column(name = "recap_year", nullable = false)
	private int recapYear;
	@Column(name = "campus_id")
	private Long campusId;
	@Column(name = "activity_date")
	private LocalDate activityDate;
	@Column(name = "grouping_id")
	private Long groupingId;
	@Column(name = "secondary_grouping_id")
	private Long secondaryGroupingId;
	@Column(name = "flag_one")
	private Boolean flagOne;
	@Column(name = "flag_two")
	private Boolean flagTwo;
	@Column(name = "flag_three")
	private Boolean flagThree;
	@Column(name = "status_value", length = 30)
	private String statusValue;
	@Column(name = "amount_value")
	private Long amountValue;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected YearlyRecapArchiveFact() {
	}

	private YearlyRecapArchiveFact(
		YearlyRecapArchiveFactType factType,
		Long sourceId,
		Long userId,
		int recapYear,
		Long campusId,
		LocalDate activityDate,
		Long groupingId,
		Long secondaryGroupingId,
		Boolean flagOne,
		Boolean flagTwo,
		Boolean flagThree,
		String statusValue,
		Long amountValue
	) {
		this.factType = factType;
		this.sourceId = sourceId;
		this.userId = userId;
		this.recapYear = recapYear;
		this.campusId = campusId;
		this.activityDate = activityDate;
		this.groupingId = groupingId;
		this.secondaryGroupingId = secondaryGroupingId;
		this.flagOne = flagOne;
		this.flagTwo = flagTwo;
		this.flagThree = flagThree;
		this.statusValue = statusValue;
		this.amountValue = amountValue;
	}

	public static YearlyRecapArchiveFact comment(Long sourceId, Long userId, int recapYear, Long campusId) {
		return new YearlyRecapArchiveFact(
			YearlyRecapArchiveFactType.COMMENT, sourceId, userId, recapYear, campusId,
			null, null, null, null, null, null, null, null);
	}

	public static YearlyRecapArchiveFact prayer(
		Long sourceId, Long userId, int recapYear, Long campusId, LocalDate weekStartDate,
		Long weekId, Long seasonId
	) {
		return new YearlyRecapArchiveFact(
			YearlyRecapArchiveFactType.PRAYER, sourceId, userId, recapYear, campusId,
			weekStartDate, weekId, seasonId, null, null, null, null, null);
	}

	public static YearlyRecapArchiveFact devotionDaily(
		Long sourceId, Long userId, int recapYear, Long campusId, LocalDate recordDate,
		boolean quietTime, boolean bibleReading, boolean prayer
	) {
		return new YearlyRecapArchiveFact(
			YearlyRecapArchiveFactType.DEVOTION_DAILY, sourceId, userId, recapYear, campusId,
			recordDate, null, null, quietTime, bibleReading, prayer, null, null);
	}

	public static YearlyRecapArchiveFact devotionWeekly(
		Long sourceId, Long userId, int recapYear, Long campusId, LocalDate weekStartDate, boolean submitted
	) {
		return new YearlyRecapArchiveFact(
			YearlyRecapArchiveFactType.DEVOTION_WEEKLY, sourceId, userId, recapYear, campusId,
			weekStartDate, null, null, submitted, null, null, null, null);
	}

	public static YearlyRecapArchiveFact penalty(
		Long sourceId, Long userId, int recapYear, Long campusId, String status, long amount
	) {
		return new YearlyRecapArchiveFact(
			YearlyRecapArchiveFactType.PENALTY, sourceId, userId, recapYear, campusId,
			null, null, null, null, null, null, status, amount);
	}

	@PrePersist
	void prePersist() {
		createdAt = Instant.now();
	}

	public YearlyRecapArchiveFactType factType() { return factType; }
	public Long sourceId() { return sourceId; }
	public Long userId() { return userId; }
	public int recapYear() { return recapYear; }
	public Long campusId() { return campusId; }
	public LocalDate activityDate() { return activityDate; }
	public Long groupingId() { return groupingId; }
	public Long secondaryGroupingId() { return secondaryGroupingId; }
	public Boolean flagOne() { return flagOne; }
	public Boolean flagTwo() { return flagTwo; }
	public Boolean flagThree() { return flagThree; }
	public String statusValue() { return statusValue; }
	public Long amountValue() { return amountValue; }
}
