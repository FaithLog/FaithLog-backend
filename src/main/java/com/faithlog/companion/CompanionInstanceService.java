package com.faithlog.companion;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.points.PointWalletService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-authoritative individual pet care and evolution.
 *
 * All mutating operations hold the existing per-user lock and a companion row lock,
 * validate request replay before versions/cooldowns and write stats + events atomically.
 */
@Service
public class CompanionInstanceService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final PointWalletService wallet;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public CompanionInstanceService(PointWalletService wallet, NamedParameterJdbcTemplate jdbc,
                                    ObjectMapper json, Clock clock) {
        this.wallet = wallet;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public record Stats(int satiety, int cleanliness, int joy, int affection, int health) {}
    public record View(UUID instanceId, String designId, int level, long growthXp,
                       long totalEarnedXp, int careDays, Stats stats, Instant lastCaredAt,
                       long version, CompanionGrowthPolicy.Evaluation nextEvolution,
                       boolean canEvolve) {}
    public record CareRequest(String action, long expectedVersion) {}
    public record EvolveRequest(long expectedVersion) {}

    private record Row(UUID id, String designId, int level, long growthXp, long totalEarnedXp,
                       int careDays, int satiety, int cleanliness, int joy, int affection,
                       int health, Instant lastEvaluatedAt, Instant lastCaredAt,
                       long elapsedHours, long xpDecayDays, LocalDate lastCareDate, LocalDate dailyXpDate,
                       int dailyXpEarned, long version) {
        private CompanionExperienceDecayPolicy.Experience experience() {
            return new CompanionExperienceDecayPolicy.Experience(
                    level, growthXp, totalEarnedXp, lastEvaluatedAt, lastCaredAt,
                    xpDecayDays);
        }
        private CompanionNeglectPolicy.Snapshot care() {
            return new CompanionNeglectPolicy.Snapshot(
                    new CompanionNeglectPolicy.Meters(satiety, cleanliness, joy, affection, health),
                    lastEvaluatedAt, lastCaredAt, elapsedHours);
        }
    }

    private static Row mapRow(ResultSet rs, int number) throws SQLException {
        java.sql.Date careDate = rs.getDate("last_care_date");
        java.sql.Date dailyDate = rs.getDate("daily_xp_date");
        return new Row(rs.getObject("id", UUID.class), rs.getString("design_id"),
                rs.getInt("level"), rs.getLong("growth_xp"), rs.getLong("total_earned_xp"),
                rs.getInt("care_days"), rs.getInt("satiety"), rs.getInt("cleanliness"),
                rs.getInt("joy"), rs.getInt("affection"), rs.getInt("health"),
                rs.getTimestamp("last_evaluated_at").toInstant(),
                rs.getTimestamp("last_cared_at").toInstant(), rs.getLong("elapsed_hours"),
                rs.getLong("xp_decay_days"),
                careDate == null ? null : careDate.toLocalDate(),
                dailyDate == null ? null : dailyDate.toLocalDate(),
                rs.getInt("daily_xp_earned"), rs.getLong("version"));
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
    }

    private static void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
        }
    }

    private Row ownedAndLocked(Long user, UUID instanceId) {
        var rows = jdbc.query(
                "SELECT * FROM companion_instances WHERE user_id=:user AND id=:id FOR UPDATE",
                new MapSqlParameterSource("user",user).addValue("id",instanceId),
                CompanionInstanceService::mapRow);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.COMPANION_NOT_OWNED);
        return rows.getFirst();
    }

    private View view(Row row, CompanionExperienceDecayPolicy.Result state, int careDays,
                      long version) {
        var xp = state.experience();
        var meters = state.care().meters();
        var stats = new Stats(meters.satiety(), meters.cleanliness(),
                meters.joy(), meters.affection(), meters.health());
        var evolution = row.designId().equals("jesus:miracle")
                ? null : CompanionGrowthPolicy.evaluate(
                        state.experience().growthState(state.care(),careDays));
        return new View(row.id(),row.designId(),xp.level(),xp.growthXp(),xp.totalEarnedXp(),
                careDays,stats,state.care().lastCaredAt(),version,evolution,
                evolution != null && evolution.canEvolve());
    }

    /** Active account check without the user-row FOR UPDATE lock used by the economy. */
    private void authenticateRead(Long user) {
        Long active = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE id=:user AND is_active=true AND deleted_at IS NULL",
                Map.of("user",user),Long.class);
        if (active == null || active != 1L) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }

    /** One fixed clock for the batch, no writes and no database locks. */
    private List<View> render(List<Row> rows, Instant now) {
        return rows.stream().map(row -> view(row,
                CompanionExperienceDecayPolicy.evaluate(row.experience(),row.care(),now),
                row.careDays(),row.version())).toList();
    }

    /** Paginated inventory view: 50 by default, 100 max, never an unbounded scan. */
    @Transactional(readOnly=true)
    public List<View> list(Long user) {
        return list(user,0,50);
    }

    @Transactional(readOnly=true)
    public List<View> list(Long user, int page, int size) {
        if (page < 0 || page > 100 || size < 1 || size > 100) throw invalid();
        authenticateRead(user);
        var rows = jdbc.query(
                "SELECT * FROM companion_instances WHERE user_id=:user "
                + "ORDER BY acquired_at,id LIMIT :size OFFSET :offset",
                new MapSqlParameterSource("user",user).addValue("size",size)
                        .addValue("offset",page * size),
                CompanionInstanceService::mapRow);
        return render(rows,clock.instant());
    }

    /**
     * Home scene reads only the five assigned UUIDs through the primary key.
     * There are no per-design scans or account locks on this read path.
     */
    @Transactional(readOnly=true)
    public List<View> gardenInstances(Long user) {
        authenticateRead(user);
        var rows = jdbc.query("""
                SELECT ci.*
                FROM garden_placements gp
                JOIN companion_instances ci
                  ON ci.user_id=gp.user_id AND ci.id=gp.instance_id
                WHERE gp.user_id=:user ORDER BY gp.slot
                """,Map.of("user",user),CompanionInstanceService::mapRow);
        return render(rows,clock.instant());
    }

    private View replay(Long user, UUID id, String key, String fingerprint) {
        var rows = jdbc.queryForList(
                "SELECT request_fingerprint,response_json FROM companion_instance_operations "
                + "WHERE user_id=:user AND instance_id=:id AND request_key=:key",
                new MapSqlParameterSource("user",user).addValue("id",id).addValue("key",key));
        if (rows.isEmpty()) return null;
        var item = rows.getFirst();
        if (!fingerprint.equals(item.get("request_fingerprint"))) {
            throw new BusinessException(ErrorCode.COMPANION_REQUEST_CONFLICT);
        }
        try {
            return json.readValue((String) item.get("response_json"),View.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored care operation cannot be decoded",e);
        }
    }

    private void record(Long user, UUID id, String key, String fingerprint, String kind,
                        Instant now, View result) {
        try {
            jdbc.update(
                "INSERT INTO companion_instance_operations"
                + "(user_id,instance_id,request_key,kind,request_fingerprint,response_json,created_at)"
                + " VALUES(:user,:id,:key,:kind,:fingerprint,:response,:now)",
                new MapSqlParameterSource("user",user).addValue("id",id)
                        .addValue("key",key).addValue("kind",kind)
                        .addValue("fingerprint",fingerprint)
                        .addValue("response",json.writeValueAsString(result))
                        .addValue("now",Timestamp.from(now)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize care operation",e);
        }
    }

    private static int bounded(int current, int delta) {
        return Math.max(0,Math.min(100,current+delta));
    }

    private static CompanionNeglectPolicy.Meters restore(
            CompanionNeglectPolicy.Meters meters, CompanionGrowthPolicy.CareAction action) {
        int food = meters.satiety();
        int clean = meters.cleanliness();
        int joy = meters.joy();
        int affection = meters.affection();
        int health = meters.health();
        switch (action) {
            case FEED -> {
                food = bounded(food,35);
                health = bounded(health,3);
                affection = bounded(affection,4);
            }
            case PET -> {
                affection = bounded(affection,22);
                joy = bounded(joy,12);
            }
            case PLAY -> {
                joy = bounded(joy,30);
                affection = bounded(affection,7);
                food = bounded(food,-3);
            }
            case CLEAN -> {
                clean = bounded(clean,45);
                health = bounded(health,5);
            }
            case SLEEP -> {
                joy = bounded(joy,15);
                health = bounded(health,15);
            }
        }
        return new CompanionNeglectPolicy.Meters(food,clean,joy,affection,health);
    }

    private static void validateVersion(long actual, long requested) {
        if (actual != requested) {
            throw new BusinessException(ErrorCode.COMPANION_INSTANCE_CONFLICT);
        }
    }

    private void persist(Long user, Row row, CompanionExperienceDecayPolicy.Experience xp,
                         CompanionNeglectPolicy.Snapshot state, int careDays,
                         LocalDate lastCareDate, LocalDate dailyXpDate, int dailyXpEarned) {
        var meters = state.meters();
        var params = new MapSqlParameterSource("user",user)
                .addValue("id",row.id()).addValue("oldVersion",row.version())
                .addValue("level",xp.level()).addValue("xp",xp.growthXp())
                .addValue("totalXp",xp.totalEarnedXp()).addValue("careDays",careDays)
                .addValue("food",meters.satiety()).addValue("clean",meters.cleanliness())
                .addValue("joy",meters.joy()).addValue("affection",meters.affection())
                .addValue("health",meters.health())
                .addValue("evaluated",Timestamp.from(state.lastEvaluatedAt()))
                .addValue("cared",Timestamp.from(state.lastCaredAt()))
                .addValue("hours",state.elapsedHours())
                .addValue("xpDecayDays",xp.evaluatedDecayDays())
                .addValue("careDate",lastCareDate)
                .addValue("xpDate",dailyXpDate).addValue("xpEarned",dailyXpEarned);
        int changed = jdbc.update(
                "UPDATE companion_instances SET level=:level,growth_xp=:xp,total_earned_xp=:totalXp,"
                + "care_days=:careDays,satiety=:food,cleanliness=:clean,joy=:joy,"
                + "affection=:affection,health=:health,last_evaluated_at=:evaluated,"
                + "last_cared_at=:cared,elapsed_hours=:hours,xp_decay_days=:xpDecayDays,"
                + "last_care_date=:careDate,"
                + "daily_xp_date=:xpDate,daily_xp_earned=:xpEarned,version=version+1 "
                + "WHERE user_id=:user AND id=:id AND version=:oldVersion",
                params);
        if (changed != 1) throw new BusinessException(ErrorCode.COMPANION_INSTANCE_CONFLICT);
    }

    @Transactional
    public View care(Long user, UUID id, String key, CareRequest request) {
        validateKey(key);
        if (id == null || request == null || request.expectedVersion() < 0 || request.action() == null) {
            throw invalid();
        }
        CompanionGrowthPolicy.CareAction action;
        try {
            action = CompanionGrowthPolicy.CareAction.valueOf(request.action());
        } catch (IllegalArgumentException e) {
            throw invalid();
        }

        wallet.lockAccount(user);
        Row row = ownedAndLocked(user,id);
        String fingerprint = "CARE:"+action.name()+":"+request.expectedVersion();
        View old = replay(user,id,key,fingerprint);
        if (old != null) return old;
        validateVersion(row.version(),request.expectedVersion());

        Instant now = clock.instant();
        var settled = CompanionExperienceDecayPolicy.evaluate(row.experience(),row.care(),now);
        if (settled.care().meters().health() == 0
                && (action == CompanionGrowthPolicy.CareAction.PET
                    || action == CompanionGrowthPolicy.CareAction.PLAY)) {
            throw new BusinessException(ErrorCode.COMPANION_CARE_UNAVAILABLE);
        }

        var params = new MapSqlParameterSource("id",id).addValue("action",action.name());
        var cooldown = jdbc.query("SELECT last_used_at FROM companion_care_cooldowns "
                + "WHERE instance_id=:id AND action=:action",params,
                (rs,n)->rs.getTimestamp(1).toInstant());
        Instant previous = cooldown.isEmpty() ? null : cooldown.getFirst();
        if (!CompanionGrowthPolicy.cooldownElapsed(now,previous,action)) {
            throw new BusinessException(ErrorCode.COMPANION_CARE_COOLDOWN);
        }

        var afterCare = CompanionExperienceDecayPolicy.markCaredFor(
                row.experience(),row.care(),now);
        LocalDate today = LocalDate.ofInstant(now,KOREA);
        int todayEarned = today.equals(row.dailyXpDate()) ? row.dailyXpEarned() : 0;
        int awarded = CompanionGrowthPolicy.growthXpAward(todayEarned,action);
        var xp = CompanionExperienceDecayPolicy.awardEarnedXp(afterCare.experience(),awarded);
        var restored = restore(afterCare.care().meters(),action);
        var cared = new CompanionNeglectPolicy.Snapshot(restored,
                afterCare.care().lastEvaluatedAt(),now,afterCare.care().elapsedHours());
        int days = Math.addExact(row.careDays(),today.equals(row.lastCareDate()) ? 0 : 1);
        persist(user,row,xp,cared,days,today,today,todayEarned+awarded);
        jdbc.update("INSERT INTO companion_care_cooldowns(instance_id,action,last_used_at) "
                + "VALUES(:id,:action,:now) ON CONFLICT(instance_id,action) DO UPDATE "
                + "SET last_used_at=EXCLUDED.last_used_at",
                params.addValue("now",Timestamp.from(now)));

        var result = new CompanionExperienceDecayPolicy.Result(
                xp,cared,afterCare.lostXp());
        View response = view(row,result,days,row.version()+1);
        record(user,id,key,fingerprint,"CARE",now,response);
        return response;
    }

    @Transactional
    public View evolve(Long user, UUID id, String key, EvolveRequest request) {
        validateKey(key);
        if (id == null || request == null || request.expectedVersion() < 0) throw invalid();
        wallet.lockAccount(user);
        Row row = ownedAndLocked(user,id);
        String fingerprint = "EVOLVE:"+request.expectedVersion();
        View old = replay(user,id,key,fingerprint);
        if (old != null) return old;
        validateVersion(row.version(),request.expectedVersion());
        if (row.designId().equals("jesus:miracle")) {
            throw new BusinessException(ErrorCode.COMPANION_EVOLUTION_NOT_READY);
        }
        Instant now = clock.instant();
        var settled = CompanionExperienceDecayPolicy.evaluate(row.experience(),row.care(),now);
        CompanionExperienceDecayPolicy.Experience evolved;
        try {
            evolved = CompanionExperienceDecayPolicy.evolve(
                    settled.experience(),settled.care(),row.careDays());
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.COMPANION_EVOLUTION_NOT_READY);
        }
        persist(user,row,evolved,settled.care(),row.careDays(),
                row.lastCareDate(),row.dailyXpDate(),row.dailyXpEarned());
        var result = new CompanionExperienceDecayPolicy.Result(
                evolved,settled.care(),settled.lostXp());
        View response = view(row,result,row.careDays(),row.version()+1);
        record(user,id,key,fingerprint,"EVOLVE",now,response);
        return response;
    }
}
