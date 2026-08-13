package com.faithlog.shepherd.domain.entity;

import com.faithlog.shepherd.domain.type.ShepherdGroupStatus;
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
	name = "shepherd_groups",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_shepherd_groups_campus_normalized_name", columnNames = {"campus_id", "normalized_name"})
	}
)
public class ShepherdGroup {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "normalized_name", nullable = false, length = 100)
	private String normalizedName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ShepherdGroupStatus status;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(nullable = false)
	private int version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ShepherdGroup() {
	}

	private ShepherdGroup(Long campusId, String name, String normalizedName, Long createdBy) {
		this.campusId = campusId;
		this.name = name;
		this.normalizedName = normalizedName;
		this.createdBy = createdBy;
		this.status = ShepherdGroupStatus.ACTIVE;
		this.version = 1;
	}

	public static ShepherdGroup create(Long campusId, String name, String normalizedName, Long createdBy) {
		return new ShepherdGroup(campusId, name, normalizedName, createdBy);
	}

	public void update(String name, String normalizedName) {
		this.name = name;
		this.normalizedName = normalizedName;
		this.version++;
	}

	public void archive() {
		this.status = ShepherdGroupStatus.ARCHIVED;
		this.version++;
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}

	public Long id() { return id; }

	public Long campusId() { return campusId; }

	public String name() { return name; }

	public String normalizedName() { return normalizedName; }

	public ShepherdGroupStatus status() { return status; }

	public Long createdBy() { return createdBy; }

	public int version() { return version; }
}
