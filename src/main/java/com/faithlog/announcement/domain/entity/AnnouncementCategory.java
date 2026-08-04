package com.faithlog.announcement.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

@Entity
@Table(name = "announcement_categories")
public class AnnouncementCategory {

	private static final int NAME_MAX_LENGTH = 30;
	private static final Pattern COLOR_PATTERN = Pattern.compile("#[0-9A-Fa-f]{6}");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Column(nullable = false, length = 7)
	private String color;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected AnnouncementCategory() {
	}

	private AnnouncementCategory(Long campusId, String name, String color, int displayOrder) {
		this.campusId = requireId(campusId, "campusId");
		this.name = normalizeName(name);
		this.color = normalizeColor(color);
		this.displayOrder = requireDisplayOrder(displayOrder);
		this.active = true;
	}

	public static AnnouncementCategory create(Long campusId, String name, String color, int displayOrder) {
		return new AnnouncementCategory(campusId, name, color, displayOrder);
	}

	public void update(String name, String color, int displayOrder) {
		this.name = normalizeName(name);
		this.color = normalizeColor(color);
		this.displayOrder = requireDisplayOrder(displayOrder);
	}

	public void deactivate() {
		this.active = false;
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

	private static Long requireId(Long value, String fieldName) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException(fieldName + " must be positive");
		}
		return value;
	}

	private static String normalizeName(String value) {
		if (value == null) {
			throw new IllegalArgumentException("name must not be null");
		}
		String normalized = value.trim();
		if (normalized.isEmpty() || normalized.length() > NAME_MAX_LENGTH) {
			throw new IllegalArgumentException("name length is invalid");
		}
		return normalized;
	}

	private static String normalizeColor(String value) {
		if (value == null || !COLOR_PATTERN.matcher(value).matches()) {
			throw new IllegalArgumentException("color must be #RRGGBB");
		}
		return value.toUpperCase(Locale.ROOT);
	}

	private static int requireDisplayOrder(int value) {
		if (value < 0) {
			throw new IllegalArgumentException("displayOrder must be non-negative");
		}
		return value;
	}

	public Long id() {
		return id;
	}

	public Long campusId() {
		return campusId;
	}

	public String name() {
		return name;
	}

	public String color() {
		return color;
	}

	public int displayOrder() {
		return displayOrder;
	}

	public boolean isActive() {
		return active;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}
}
