package com.faithlog.shepherd.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "shepherd_group_assignees")
@IdClass(ShepherdGroupAssigneeId.class)
public class ShepherdGroupAssignee {

	@Id
	@Column(name = "shepherd_group_id", nullable = false)
	private Long shepherdGroupId;

	@Id
	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "assigned_by", nullable = false)
	private Long assignedBy;

	@Column(name = "assigned_at", nullable = false)
	private Instant assignedAt;

	protected ShepherdGroupAssignee() {
	}

	private ShepherdGroupAssignee(Long shepherdGroupId, Long campusId, Long userId, Long assignedBy) {
		this.shepherdGroupId = shepherdGroupId;
		this.campusId = campusId;
		this.userId = userId;
		this.assignedBy = assignedBy;
	}

	public static ShepherdGroupAssignee create(Long shepherdGroupId, Long campusId, Long userId, Long assignedBy) {
		return new ShepherdGroupAssignee(shepherdGroupId, campusId, userId, assignedBy);
	}

	@PrePersist
	void prePersist() {
		if (assignedAt == null) {
			assignedAt = Instant.now();
		}
	}

	public Long shepherdGroupId() { return shepherdGroupId; }

	public Long campusId() { return campusId; }

	public Long userId() { return userId; }
}
