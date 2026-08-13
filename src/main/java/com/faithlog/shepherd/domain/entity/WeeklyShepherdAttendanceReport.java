package com.faithlog.shepherd.domain.entity;

import com.faithlog.shepherd.domain.type.WeeklyShepherdAttendanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
	name = "weekly_shepherd_attendance_reports",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_weekly_shepherd_attendance_group_date", columnNames = {"shepherd_group_id", "service_date"})
	}
)
public class WeeklyShepherdAttendanceReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "shepherd_group_id", nullable = false)
	private Long shepherdGroupId;

	@Column(name = "service_date", nullable = false)
	private LocalDate serviceDate;

	@Column(name = "small_group_meeting_count", nullable = false)
	private int smallGroupMeetingCount;

	@Column(name = "holy_wave_count", nullable = false)
	private int holyWaveCount;

	@Column(name = "other_worship_count", nullable = false)
	private int otherWorshipCount;

	@Column(length = 500)
	private String note;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private WeeklyShepherdAttendanceStatus status;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(name = "last_modified_by", nullable = false)
	private Long lastModifiedBy;

	@Column(name = "last_modified_at", nullable = false)
	private Instant lastModifiedAt;

	@Column(nullable = false)
	private int version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected WeeklyShepherdAttendanceReport() {
	}

	private WeeklyShepherdAttendanceReport(
		Long campusId,
		Long shepherdGroupId,
		LocalDate serviceDate,
		int smallGroupMeetingCount,
		int holyWaveCount,
		int otherWorshipCount,
		String note,
		WeeklyShepherdAttendanceStatus status,
		Long requesterId,
		Instant now
	) {
		this.campusId = campusId;
		this.shepherdGroupId = shepherdGroupId;
		this.serviceDate = serviceDate;
		this.smallGroupMeetingCount = smallGroupMeetingCount;
		this.holyWaveCount = holyWaveCount;
		this.otherWorshipCount = otherWorshipCount;
		this.note = note;
		this.status = status;
		this.createdBy = requesterId;
		this.lastModifiedBy = requesterId;
		this.lastModifiedAt = now;
		this.version = 1;
	}

	public static WeeklyShepherdAttendanceReport create(
		Long campusId,
		Long shepherdGroupId,
		LocalDate serviceDate,
		int smallGroupMeetingCount,
		int holyWaveCount,
		int otherWorshipCount,
		String note,
		WeeklyShepherdAttendanceStatus status,
		Long requesterId,
		Instant now
	) {
		return new WeeklyShepherdAttendanceReport(
			campusId, shepherdGroupId, serviceDate, smallGroupMeetingCount, holyWaveCount, otherWorshipCount,
			note, status, requesterId, now);
	}

	public void update(
		int smallGroupMeetingCount,
		int holyWaveCount,
		int otherWorshipCount,
		String note,
		WeeklyShepherdAttendanceStatus status,
		Long requesterId,
		Instant now
	) {
		this.smallGroupMeetingCount = smallGroupMeetingCount;
		this.holyWaveCount = holyWaveCount;
		this.otherWorshipCount = otherWorshipCount;
		this.note = note;
		this.status = status;
		this.lastModifiedBy = requesterId;
		this.lastModifiedAt = now;
		this.version++;
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (lastModifiedAt == null) {
			lastModifiedAt = now;
		}
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}

	public Long id() { return id; }

	public Long campusId() { return campusId; }

	public Long shepherdGroupId() { return shepherdGroupId; }

	public LocalDate serviceDate() { return serviceDate; }

	public int smallGroupMeetingCount() { return smallGroupMeetingCount; }

	public int holyWaveCount() { return holyWaveCount; }

	public int otherWorshipCount() { return otherWorshipCount; }

	public String note() { return note; }

	public WeeklyShepherdAttendanceStatus status() { return status; }

	public Long lastModifiedBy() { return lastModifiedBy; }

	public Instant lastModifiedAt() { return lastModifiedAt; }

	public int version() { return version; }
}
