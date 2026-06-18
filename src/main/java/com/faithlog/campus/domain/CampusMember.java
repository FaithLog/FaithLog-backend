package com.faithlog.campus.domain;

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

@Entity
@Table(
	name = "campus_members",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_campus_members_campus_user", columnNames = {"campus_id", "user_id"})
	}
)
public class CampusMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "campus_role", nullable = false, length = 30)
	private CampusRole campusRole;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CampusMemberStatus status;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CampusMember() {
	}

	private CampusMember(Long campusId, Long userId, CampusRole campusRole) {
		this.campusId = campusId;
		this.userId = userId;
		this.campusRole = campusRole;
		this.status = CampusMemberStatus.ACTIVE;
		this.joinedAt = Instant.now();
	}

	public static CampusMember createMinister(Long campusId, Long userId) {
		return new CampusMember(campusId, userId, CampusRole.MINISTER);
	}

	public static CampusMember createMember(Long campusId, Long userId) {
		return new CampusMember(campusId, userId, CampusRole.MEMBER);
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (this.joinedAt == null) {
			this.joinedAt = now;
		}
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}

	public boolean isActive() {
		return status == CampusMemberStatus.ACTIVE;
	}

	public boolean canViewInviteCode() {
		return campusRole == CampusRole.MINISTER
			|| campusRole == CampusRole.ELDER
			|| campusRole == CampusRole.CAMPUS_LEADER;
	}

	public boolean canManageCampusMembers() {
		return isActive() && campusRole.canManageCampusMembers();
	}

	public void deactivate() {
		this.status = CampusMemberStatus.INACTIVE;
	}

	public void reactivateAsMember() {
		this.campusRole = CampusRole.MEMBER;
		this.status = CampusMemberStatus.ACTIVE;
		this.joinedAt = Instant.now();
	}

	public void changeCampusRole(CampusRole campusRole) {
		this.campusRole = campusRole;
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

	public CampusRole campusRole() {
		return campusRole;
	}

	public CampusMemberStatus status() {
		return status;
	}
}
