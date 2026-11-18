package com.faithlog.companion;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.points.PointWalletService;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GardenService {
    private final PointWalletService accounts;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public GardenService(PointWalletService accounts, NamedParameterJdbcTemplate jdbc,
                         Clock clock) {
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private void initialize(Long user) {
        // Serializes with draws and sales while an equipped UUID is assigned.
        accounts.lockAccount(user);
        StarterBackgrounds.grant(jdbc, user);
        jdbc.update("""
            INSERT INTO user_gardens(user_id,version,updated_at)
            VALUES(:user,0,:now) ON CONFLICT(user_id) DO NOTHING
            """,new MapSqlParameterSource("user",user)
                .addValue("now",Timestamp.from(clock.instant())));
    }

    @Transactional(readOnly=true)
    public Garden get(Long user) {
        Long active = jdbc.queryForObject("""
                SELECT count(*) FROM users
                WHERE id=:user AND is_active=true AND deleted_at IS NULL
                """,Map.of("user",user),Long.class);
        if (active == null || active != 1L) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        return read(user);
    }

    private Garden read(Long user) {
        var params = Map.of("user",user);
        var placements = jdbc.query("""
                SELECT slot,design_id,x,y,instance_id FROM garden_placements
                WHERE user_id=:user ORDER BY slot
                """,params,(rs,n) -> new GardenUpdate.Placement(
                rs.getInt("slot"),rs.getString("design_id"),rs.getDouble("x"),
                rs.getDouble("y"),rs.getObject("instance_id",UUID.class)));
        var gardens = jdbc.query("""
                SELECT version,background_id FROM user_gardens WHERE user_id=:user
                """,params,(rs,n) -> new Garden(
                rs.getLong("version"),rs.getString("background_id"),placements));
        return gardens.isEmpty() ? new Garden(0,null,List.of()) : gardens.getFirst();
    }

    /** Resolve no more than five slots, using index lookups instead of a full inventory scan. */
    private List<GardenUpdate.Placement> resolvePlacements(
            Long user, List<GardenUpdate.Placement> requested,
            List<GardenUpdate.Placement> previous) {
        var selected = new HashSet<UUID>();
        var previousByDesign = new HashMap<String,UUID>();
        for (var p : previous) {
            // An old client can never specify which of two identical designs to use.
            previousByDesign.putIfAbsent(p.designId(),p.instanceId());
        }

        var explicit = requested.stream().filter(p -> p.instanceId()!=null).toList();
        var valid = new HashMap<UUID,String>();
        if (!explicit.isEmpty()) {
            var params = new MapSqlParameterSource("user",user)
                    .addValue("ids",explicit.stream().map(GardenUpdate.Placement::instanceId).toList());
            jdbc.query("""
                    SELECT id,design_id FROM companion_instances
                    WHERE user_id=:user AND id IN (:ids)
                    """,params,(rs,n) -> {
                        valid.put(rs.getObject("id",UUID.class),rs.getString("design_id"));
                        return 1;
                    });
        }
        for (var p : explicit) {
            if (!p.designId().equals(valid.get(p.instanceId()))
                    || !selected.add(p.instanceId())) {
                throw new BusinessException(ErrorCode.COMPANION_NOT_OWNED);
            }
        }

        var result = new ArrayList<GardenUpdate.Placement>(requested.size());
        for (var p : requested) {
            UUID id = p.instanceId();
            if (id == null) {
                var previousId = previousByDesign.get(p.designId());
                if (previousId != null && !selected.contains(previousId)) {
                    id = previousId;
                } else {
                    // At most five indexed LIMIT 1 queries and no repeated
                    // requests while characters move on the device.
                    var params = new MapSqlParameterSource("user",user)
                            .addValue("design",p.designId());
                    String query = """
                        SELECT id FROM companion_instances
                        WHERE user_id=:user AND design_id=:design
                        """;
                    if (!selected.isEmpty()) {
                        params.addValue("selected",List.copyOf(selected));
                        query += " AND id NOT IN (:selected)";
                    }
                    query += " ORDER BY acquired_at,id LIMIT 1";
                    var options = jdbc.query(query,params,
                            (rs,n) -> rs.getObject("id",UUID.class));
                    if (options.isEmpty()) {
                        throw new BusinessException(ErrorCode.COMPANION_NOT_OWNED);
                    }
                    id = options.getFirst();
                }
                if (!selected.add(id)) {
                    throw new BusinessException(ErrorCode.COMPANION_NOT_OWNED);
                }
            }
            result.add(new GardenUpdate.Placement(
                    p.slot(),p.designId(),p.x(),p.y(),id));
        }
        return List.copyOf(result);
    }

    @Transactional
    public Garden save(Long user, GardenUpdate request) {
        initialize(user);
        var current = read(user);
        if (current.version()!=request.version()) {
            throw new BusinessException(ErrorCode.COMPANION_GARDEN_CONFLICT);
        }
        var params = new MapSqlParameterSource("user",user)
                .addValue("background",request.backgroundId());
        if (request.backgroundId()!=null) {
            Long owned = jdbc.queryForObject("""
                    SELECT count(*) FROM user_backgrounds
                    WHERE user_id=:user AND background_id=:background
                    """,params,Long.class);
            if (owned == null || owned != 1) {
                throw new BusinessException(ErrorCode.COMPANION_NOT_OWNED);
            }
        }
        var placements = resolvePlacements(user,request.placements(),current.placements());
        params.addValue("version",request.version())
                .addValue("now",Timestamp.from(clock.instant()));
        int changed = jdbc.update("""
                UPDATE user_gardens SET background_id=:background,
                version=version+1,updated_at=:now
                WHERE user_id=:user AND version=:version
                """,params);
        if (changed!=1) {
            throw new BusinessException(ErrorCode.COMPANION_GARDEN_CONFLICT);
        }

        // A small bounded write (<=5 slots) happens only on a debounced PUT,
        // not on character movement or garden GET.
        jdbc.update("DELETE FROM garden_placements WHERE user_id=:user",params);
        for (var p : placements) {
            jdbc.update("""
                    INSERT INTO garden_placements(user_id,slot,design_id,x,y,instance_id)
                    VALUES(:user,:slot,:design,:x,:y,:id)
                    """,new MapSqlParameterSource("user",user)
                    .addValue("slot",p.slot()).addValue("design",p.designId())
                    .addValue("x",p.x()).addValue("y",p.y())
                    .addValue("id",p.instanceId()));
        }
        return read(user);
    }

    public record Garden(long version, String backgroundId,
                         List<GardenUpdate.Placement> placements) {}
}
