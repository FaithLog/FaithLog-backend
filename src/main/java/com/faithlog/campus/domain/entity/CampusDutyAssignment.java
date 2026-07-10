package com.faithlog.campus.domain.entity;

import com.faithlog.campus.domain.type.DutyType;
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
import java.time.Instant;

@Entity
@Table(name = "campus_duty_assignments")
public class CampusDutyAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "duty_type", nullable = false, length = 30)
	private DutyType dutyType;

	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	@Column(name = "assigned_at", nullable = false)
	private Instant assignedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CampusDutyAssignment() {
	}

	private CampusDutyAssignment(Long campusId, Long userId, DutyType dutyType) {
		this.campusId = campusId;
		this.userId = userId;
		this.dutyType = dutyType;
		this.isActive = true;
		this.assignedAt = Instant.now();
	}

	public static CampusDutyAssignment assignCoffee(Long campusId, Long userId) {
		return new CampusDutyAssignment(campusId, userId, DutyType.COFFEE);
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (this.assignedAt == null) {
			this.assignedAt = now;
		}
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}

	public void revoke() {
		this.isActive = false;
		this.revokedAt = Instant.now();
	}

	public Long id() {
		return id;
	}

	public Long campusId() {
		return campusId;
	}

	public Long userId() {
		return userId;
	}

	public DutyType dutyType() {
		return dutyType;
	}

	public boolean isActive() {
		return isActive;
	}

	public Instant assignedAt() {
		return assignedAt;
	}
}
