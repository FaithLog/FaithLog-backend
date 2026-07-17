package com.faithlog.user.domain.entity;

import com.faithlog.user.domain.type.UserRole;
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
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private UserRole role;

	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "token_version", nullable = false, columnDefinition = "bigint default 0")
	private long tokenVersion;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	private User(String name, String email, String passwordHash) {
		this.name = name;
		this.email = email;
		this.passwordHash = passwordHash;
		this.role = UserRole.USER;
		this.isActive = true;
		this.tokenVersion = 0L;
	}

	public static User create(String name, String email, String passwordHash) {
		return new User(name, email, passwordHash);
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

	public void updateLastLoginAt(Instant lastLoginAt) {
		this.lastLoginAt = lastLoginAt;
	}

	public void changeRole(UserRole role) {
		if (this.role == role) {
			return;
		}
		this.role = role;
		increaseTokenVersion();
	}

	public void increaseTokenVersion() {
		this.tokenVersion++;
	}

	public void deleteAccount(String anonymizedEmail, String anonymizedName, String disabledPasswordHash, Instant deletedAt) {
		this.email = anonymizedEmail;
		this.name = anonymizedName;
		this.passwordHash = disabledPasswordHash;
		this.isActive = false;
		this.deletedAt = deletedAt;
		increaseTokenVersion();
	}

	public Long id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String email() {
		return email;
	}

	public String passwordHash() {
		return passwordHash;
	}

	public UserRole role() {
		return role;
	}

	public boolean isActive() {
		return isActive;
	}

	public boolean isAdmin() {
		return role == UserRole.ADMIN;
	}

	public long tokenVersion() {
		return tokenVersion;
	}

	public Instant lastLoginAt() {
		return lastLoginAt;
	}

	public Instant deletedAt() {
		return deletedAt;
	}
}
