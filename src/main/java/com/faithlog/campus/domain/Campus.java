package com.faithlog.campus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "campuses")
public class Campus {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 100)
	private String region;

	@Column(columnDefinition = "text")
	private String description;

	@Column(name = "invite_code", nullable = false, unique = true, length = 50)
	private String inviteCode;

	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Campus() {
	}

	private Campus(String name, String region, String description, String inviteCode) {
		this.name = name;
		this.region = region;
		this.description = description;
		this.inviteCode = inviteCode;
		this.isActive = true;
	}

	public static Campus create(String name, String region, String description, String inviteCode) {
		return new Campus(name, region, description, inviteCode);
	}

	public void update(String name, String region, String description, Boolean isActive) {
		if (name != null) {
			this.name = name;
		}
		if (region != null) {
			this.region = region;
		}
		if (description != null) {
			this.description = description;
		}
		if (isActive != null) {
			this.isActive = isActive;
		}
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

	public Long id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String region() {
		return region;
	}

	public String description() {
		return description;
	}

	public String inviteCode() {
		return inviteCode;
	}

	public boolean isActive() {
		return isActive;
	}
}
