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
    private static final int SEASON_TARGET = 3;
    private static final int SEASON_REWARD = 1;

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
        return snapshot(userId, read(userId), clock.instant());
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
        return snapshot(userId, expedition, now);
    }

    @Transactional
    public Status claim(long userId, UUID id) {
        accounts.lockAccount(userId);
        var now = clock.instant();
        var current = read(userId);
        if (current == null || !current.id().equals(id) || now.isBefore(current.readyAt())) throw unavailable();
        if (current.claimed() != null) return snapshot(userId, current, now);
        jdbc.update("UPDATE garden_expeditions SET claimed_at = :now WHERE user_id = :user",
            Map.of("now", Timestamp.from(now), "user", userId));
        grantShards(userId, REWARD);
        var season = seasonOf(now);
        jdbc.update("""
            INSERT INTO garden_season_progress(user_id, season_start, completed) VALUES (:user, :start, 1)
            ON CONFLICT (user_id, season_start) DO UPDATE
              SET completed = LEAST(garden_season_progress.completed + 1, :target)
            """, Map.of("user", userId, "start", season.startsOn(), "target", SEASON_TARGET));
        return snapshot(userId, new Expedition(current.id(), current.designId(), current.startedOn(), current.readyAt(), now), now);
    }

    @Transactional
    public Status claimSeason(long userId, LocalDate seasonStart) {
        accounts.lockAccount(userId);
        var now = clock.instant();
        var season = seasonOf(now);
        if (!season.startsOn().equals(seasonStart)) throw seasonUnavailable();
        var progress = readSeason(userId, season);
        if (progress.completed() < SEASON_TARGET) throw seasonUnavailable();
        if (!progress.claimed()) {
            jdbc.update("""
                UPDATE garden_season_progress SET claimed = TRUE
                WHERE user_id = :user AND season_start = :start
                """, Map.of("user", userId, "start", seasonStart));
            grantShards(userId, SEASON_REWARD);
        }
        return snapshot(userId, read(userId), now);
    }

    private void grantShards(long userId, int reward) {
        jdbc.update("""
            INSERT INTO companion_growth_shards(user_id, balance) VALUES (:user, :reward)
            ON CONFLICT (user_id) DO UPDATE SET balance = companion_growth_shards.balance + EXCLUDED.balance
            """, Map.of("user", userId, "reward", reward));
    }

    static Season seasonOf(Instant now) {
        var date = LocalDate.ofInstant(now, KOREA);
        int month = date.getMonthValue();
        int startMonth = month <= 2 || month == 12 ? 12 : month <= 5 ? 3 : month <= 8 ? 6 : 9;
        int year = month <= 2 ? date.getYear() - 1 : date.getYear();
        var start = LocalDate.of(year, startMonth, 1);
        var name = switch (startMonth) {
            case 3 -> "SPRING";
            case 6 -> "SUMMER";
            case 9 -> "AUTUMN";
            default -> "WINTER";
        };
        return new Season(name, start, start.plusMonths(3).minusDays(1));
    }

    private SeasonProgress readSeason(long userId, Season season) {
        return jdbc.query("""
            SELECT completed, claimed FROM garden_season_progress
            WHERE user_id = :user AND season_start = :start
            """, Map.of("user", userId, "start", season.startsOn()),
            (rs, index) -> new SeasonProgress(season.name(), season.startsOn(), season.endsOn(),
                rs.getInt("completed"), SEASON_TARGET, SEASON_REWARD, rs.getBoolean("claimed")))
            .stream().findFirst().orElseGet(() -> new SeasonProgress(season.name(), season.startsOn(),
                season.endsOn(), 0, SEASON_TARGET, SEASON_REWARD, false));
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

    private Status snapshot(long userId, Expedition expedition, Instant now) {
        var season = readSeason(userId, seasonOf(now));
        if (expedition == null) return new Status("AVAILABLE", null, null, null, REWARD, season);
        var state = expedition.claimed() != null
            ? (expedition.startedOn().equals(LocalDate.ofInstant(now, KOREA)) ? "CLAIMED" : "AVAILABLE")
            : (now.isBefore(expedition.readyAt()) ? "EXPLORING" : "READY");
        return new Status(state, expedition.id(), expedition.designId(), expedition.readyAt(), REWARD, season);
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.COMPANION_EXPEDITION_UNAVAILABLE);
    }

    private static BusinessException seasonUnavailable() {
        return new BusinessException(ErrorCode.COMPANION_SEASON_REWARD_UNAVAILABLE);
    }

    public record Status(String state, UUID id, String designId, Instant readyAt, int reward, SeasonProgress season) {}
    public record SeasonProgress(String name, LocalDate startsOn, LocalDate endsOn,
                                 int completed, int target, int reward, boolean claimed) {}
    record Season(String name, LocalDate startsOn, LocalDate endsOn) {}
    private record Expedition(UUID id, String designId, LocalDate startedOn, Instant readyAt, Instant claimed) {}
}
