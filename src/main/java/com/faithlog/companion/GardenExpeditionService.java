package com.faithlog.companion;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.points.PointWalletService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GardenExpeditionService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final Duration DURATION = Duration.ofMinutes(30);
    private static final int REWARD = 1;

    private final NamedParameterJdbcTemplate jdbc;
    private final PointWalletService accounts;
    private final Clock clock;

    public GardenExpeditionService(NamedParameterJdbcTemplate jdbc, PointWalletService accounts, Clock clock) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Status status(long userId) {
        return snapshot(read(userId), clock.instant());
    }

    @Transactional
    public Status start(long userId, UUID instanceId) {
        accounts.lockAccount(userId);
        var now = clock.instant();
        var today = LocalDate.ofInstant(now, KOREA);
        var current = read(userId);
        if (current != null && (current.claimed() == null || current.startedOn().equals(today))) {
            throw unavailable();
        }
        var designs = jdbc.queryForList("""
            SELECT design_id FROM garden_placements
            WHERE user_id = :user AND instance_id = :instance
            """, Map.of("user", userId, "instance", instanceId), String.class);
        if (designs.isEmpty()) throw new BusinessException(ErrorCode.COMPANION_NOT_OWNED);
        var expedition = new Expedition(UUID.randomUUID(), designs.getFirst(), today, now.plus(DURATION), null);
        jdbc.update("""
            INSERT INTO garden_expeditions(user_id, id, design_id, started_on, ready_at)
            VALUES (:user, :id, :design, :today, :ready)
            ON CONFLICT (user_id) DO UPDATE SET id = EXCLUDED.id, design_id = EXCLUDED.design_id,
                started_on = EXCLUDED.started_on, ready_at = EXCLUDED.ready_at, claimed_at = NULL
            """, Map.of("user", userId, "id", expedition.id(), "design", expedition.designId(),
                "today", today, "ready", Timestamp.from(expedition.readyAt())));
        return snapshot(expedition, now);
    }

    @Transactional
    public Status claim(long userId, UUID id) {
        accounts.lockAccount(userId);
        var now = clock.instant();
        var current = read(userId);
        if (current == null || !current.id().equals(id) || now.isBefore(current.readyAt())) throw unavailable();
        if (current.claimed() != null) return snapshot(current, now);
        jdbc.update("UPDATE garden_expeditions SET claimed_at = :now WHERE user_id = :user",
            Map.of("now", Timestamp.from(now), "user", userId));
        jdbc.update("""
            INSERT INTO companion_growth_shards(user_id, balance) VALUES (:user, :reward)
            ON CONFLICT (user_id) DO UPDATE SET balance = companion_growth_shards.balance + EXCLUDED.balance
            """, Map.of("user", userId, "reward", REWARD));
        return snapshot(new Expedition(current.id(), current.designId(), current.startedOn(), current.readyAt(), now), now);
    }

    private Expedition read(long userId) {
        return jdbc.query("""
            SELECT id, design_id, started_on, ready_at, claimed_at
            FROM garden_expeditions WHERE user_id = :user
            """, Map.of("user", userId), (rs, index) -> {
                var claimed = rs.getTimestamp("claimed_at");
                return new Expedition(rs.getObject("id", UUID.class), rs.getString("design_id"),
                    rs.getObject("started_on", LocalDate.class), rs.getTimestamp("ready_at").toInstant(),
                    claimed == null ? null : claimed.toInstant());
            }).stream().findFirst().orElse(null);
    }

    private Status snapshot(Expedition expedition, Instant now) {
        if (expedition == null) return new Status("AVAILABLE", null, null, null, REWARD);
        var state = expedition.claimed() != null
            ? (expedition.startedOn().equals(LocalDate.ofInstant(now, KOREA)) ? "CLAIMED" : "AVAILABLE")
            : (now.isBefore(expedition.readyAt()) ? "EXPLORING" : "READY");
        return new Status(state, expedition.id(), expedition.designId(), expedition.readyAt(), REWARD);
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.COMPANION_EXPEDITION_UNAVAILABLE);
    }

    public record Status(String state, UUID id, String designId, Instant readyAt, int reward) {}
    private record Expedition(UUID id, String designId, LocalDate startedOn, Instant readyAt, Instant claimed) {}
}
