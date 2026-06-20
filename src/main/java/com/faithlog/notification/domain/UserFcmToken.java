package com.faithlog.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
	name = "user_fcm_tokens",
	indexes = {
		@Index(name = "idx_user_fcm_tokens_user_client_active", columnList = "user_id, client_instance_id, is_active")
	}
)
public class UserFcmToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, unique = true, columnDefinition = "text")
	private String token;

	@Column(name = "client_instance_id", nullable = false, length = 100)
	private String clientInstanceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "device_type", nullable = false, length = 30)
	private DeviceType deviceType;

	@Column(name = "app_version", length = 50)
	private String appVersion;

	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	@Column(name = "last_seen_at")
	private Instant lastSeenAt;

	@Column(name = "last_refreshed_at")
	private Instant lastRefreshedAt;

	@Column(name = "last_failure_reason", columnDefinition = "text")
	private String lastFailureReason;

	@Column(name = "deactivated_at")
	private Instant deactivatedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserFcmToken() {
	}

	private UserFcmToken(
		Long userId,
		String token,
		String clientInstanceId,
		DeviceType deviceType,
		String appVersion
	) {
		this.userId = userId;
		this.token = token;
		this.clientInstanceId = clientInstanceId;
		this.deviceType = deviceType;
		this.appVersion = appVersion;
		this.isActive = true;
		Instant now = Instant.now();
		this.lastSeenAt = now;
		this.lastRefreshedAt = now;
	}

	public static UserFcmToken create(
		Long userId,
		String token,
		String clientInstanceId,
		DeviceType deviceType,
		String appVersion
	) {
		return new UserFcmToken(userId, token, clientInstanceId, deviceType, appVersion);
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (lastSeenAt == null) {
			lastSeenAt = now;
		}
		if (lastRefreshedAt == null) {
			lastRefreshedAt = now;
		}
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}

	public void refresh(
		Long userId,
		String clientInstanceId,
		DeviceType deviceType,
		String appVersion
	) {
		this.userId = userId;
		this.clientInstanceId = clientInstanceId;
		this.deviceType = deviceType;
		this.appVersion = appVersion;
		this.isActive = true;
		this.deactivatedAt = null;
		this.lastFailureReason = null;
		Instant now = Instant.now();
		this.lastSeenAt = now;
		this.lastRefreshedAt = now;
	}

	public void deactivate() {
		if (!isActive) {
			return;
		}
		this.isActive = false;
		this.deactivatedAt = Instant.now();
	}

	public void recordFailure(String failureReason) {
		this.lastFailureReason = failureReason;
	}

	public Long id() {
		return id;
	}

	public Long userId() {
		return userId;
	}

	public String token() {
		return token;
	}

	public String clientInstanceId() {
		return clientInstanceId;
	}

	public DeviceType deviceType() {
		return deviceType;
	}

	public String appVersion() {
		return appVersion;
	}

	public boolean isActive() {
		return isActive;
	}

	public Instant lastSeenAt() {
		return lastSeenAt;
	}

	public Instant lastRefreshedAt() {
		return lastRefreshedAt;
	}

	public String lastFailureReason() {
		return lastFailureReason;
	}

	public Instant deactivatedAt() {
		return deactivatedAt;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}
}
