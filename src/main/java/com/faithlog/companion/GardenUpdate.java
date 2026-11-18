package com.faithlog.companion;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record GardenUpdate(long version, String backgroundId, List<Placement> placements,
                           String placementProtocol) {
    public static final String INSTANCE_PROTOCOL = "INSTANCE_V1";

    /** Existing 1.3-era requests and backend callers omit the protocol. */
    public GardenUpdate(long version, String backgroundId, List<Placement> placements) {
        this(version, backgroundId, placements, null);
    }
    public GardenUpdate {
        if (placementProtocol != null && !INSTANCE_PROTOCOL.equals(placementProtocol)) {
            throw invalid();
        }
        if (version < 0 || backgroundId != null
                && (backgroundId.isBlank() || backgroundId.length() > 40)
                || placements == null || placements.size() > 5
                || placements.stream().anyMatch(Objects::isNull)) {
            throw invalid();
        }
        var validated = List.copyOf(placements);
        placements = validated;
        if (placements.stream().map(Placement::slot).distinct().count() != placements.size()
                || placements.stream().map(Placement::instanceId).filter(Objects::nonNull)
                        .distinct().count() != placements.stream()
                                .filter(p -> p.instanceId() != null).count()) {
            throw invalid();
        }
        // Old apps send designId only and cannot distinguish duplicate styles.
        // They retain their previous one-per-design contract.
        var legacy = placements.stream().filter(p -> p.instanceId() == null).toList();
        if (legacy.stream().map(Placement::designId).distinct().count() != legacy.size()
                || legacy.stream().anyMatch(p -> validated.stream()
                        .anyMatch(other -> other != p
                                && other.designId().equals(p.designId())))) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
    }

    /** Optional instanceId maintains compatibility with 1.3-era clients. */
    public record Placement(int slot, String designId, double x, double y, UUID instanceId) {
        public Placement(int slot, String designId, double x, double y) {
            this(slot, designId, x, y, null);
        }

        public Placement {
            if (slot < 1 || slot > 5 || designId == null || designId.isBlank()
                    || designId.length() > 100 || !Double.isFinite(x)
                    || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1) {
                throw invalid();
            }
        }
    }
}
