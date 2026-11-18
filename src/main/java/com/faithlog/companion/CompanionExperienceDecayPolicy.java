package com.faithlog.companion;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Lazy experience decay for one owned companion.
 *
 * <p>This policy is not wired to a controller yet. A future service must load
 * both snapshots under the same individual-companion lock and persist both
 * snapshots atomically. Only a successful, idempotent care action may reset
 * the grace clock or award experience.
 */
public final class CompanionExperienceDecayPolicy {
    private CompanionExperienceDecayPolicy() {}

    public static final long GRACE_HOURS = 48;
    public static final long DECAY_INTERVAL_HOURS = 24;
    public static final int NORMAL_DAILY_LOSS = 10;
    public static final int DANGER_DAILY_LOSS = 20;

    /** The level floor protects completed evolution; XP above that floor may decline. */
    public static long floorForLevel(int level) {
        return switch (level) {
            case 1 -> 0L;
            case 2 -> 300L;
            case 3 -> 1000L;
            default -> throw new IllegalArgumentException("Unknown evolution level");
        };
    }

    /**
     * growthXp is the current spendable-for-evolution progress.
     * totalEarnedXp is an audit/history counter and never declines.
     * Both timestamps must track the matching care-meter snapshot.
     */
    public record Experience(int level, long growthXp, long totalEarnedXp,
                             Instant lastEvaluatedAt, Instant lastCaredAt) {
        public Experience {
            long floor = floorForLevel(level);
            if (growthXp < floor || totalEarnedXp < growthXp) {
                throw new IllegalArgumentException("Invalid experience or evolution floor");
            }
            Objects.requireNonNull(lastEvaluatedAt, "lastEvaluatedAt");
            Objects.requireNonNull(lastCaredAt, "lastCaredAt");
        }

        public CompanionGrowthPolicy.State growthState(
                CompanionNeglectPolicy.Snapshot care, int distinctCareDays) {
            return care.growthState(level, growthXp, distinctCareDays);
        }
    }

    public record Result(Experience experience,
                         CompanionNeglectPolicy.Snapshot care,
                         long lostXp) {
        public Result {
            Objects.requireNonNull(experience, "experience");
            Objects.requireNonNull(care, "care");
            if (lostXp < 0) throw new IllegalArgumentException("Negative XP loss");
        }
    }

    /**
     * Calculate loss at exact 72h, 96h, 120h ... boundaries since the last care.
     * A danger penalty applies only when hunger or cleanliness is below 25
     * AT THAT BOUNDARY; becoming sick later cannot rewrite earlier days.
     *
     * <p>Both snapshots must represent one consistent earlier server state.
     * Repeated reads are idempotent and do not restart the 48h grace period.
     */
    public static Result evaluate(Experience previous,
                                  CompanionNeglectPolicy.Snapshot care,
                                  Instant now) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(care, "care");
        Objects.requireNonNull(now, "now");
        if (!previous.lastEvaluatedAt().equals(care.lastEvaluatedAt())
                || !previous.lastCaredAt().equals(care.lastCaredAt())) {
            throw new IllegalArgumentException("Experience and care snapshots are out of sync");
        }
        if (now.isBefore(previous.lastEvaluatedAt())) {
            return new Result(previous, care, 0L);
        }

        var updatedCare = CompanionNeglectPolicy.evaluate(care, now);
        Instant through = updatedCare.lastEvaluatedAt();
        long firstTick = elapsedDecayDays(previous.lastCaredAt(), previous.lastEvaluatedAt());
        long lastTick = elapsedDecayDays(previous.lastCaredAt(), through);
        long currentXp = previous.growthXp();
        long floor = floorForLevel(previous.level());
        long lostXp = 0L;

        // This loop short-circuits as soon as the floor is reached. Even a
        // long absence does not require calculating every missed day.
        for (long tick = firstTick + 1; tick <= lastTick && currentXp > floor; tick++) {
            long hoursSinceCare = Math.addExact(GRACE_HOURS,
                    Math.multiplyExact(tick, DECAY_INTERVAL_HOURS));
            Instant atTick = previous.lastCaredAt()
                    .plus(hoursSinceCare, ChronoUnit.HOURS);
            var careAtTick = CompanionNeglectPolicy.evaluate(care, atTick).meters();
            boolean danger = careAtTick.satiety() < CompanionNeglectPolicy.LOW_METER_THRESHOLD
                    || careAtTick.cleanliness() < CompanionNeglectPolicy.LOW_METER_THRESHOLD;
            long requestedLoss = danger ? DANGER_DAILY_LOSS : NORMAL_DAILY_LOSS;
            long appliedLoss = Math.min(requestedLoss, currentXp - floor);
            currentXp -= appliedLoss;
            lostXp += appliedLoss;
        }

        return new Result(new Experience(previous.level(), currentXp,
                previous.totalEarnedXp(), through, previous.lastCaredAt()),
                updatedCare, lostXp);
    }

    /**
     * To be called only AFTER a successful care operation and its cooldown check.
     * Calculate previously due loss first; then restart the 48-hour grace timer.
     * Actual stat restoration and XP award belong to the future care service.
     */
    public static Result markCaredFor(Experience previous,
                                      CompanionNeglectPolicy.Snapshot care,
                                      Instant now) {
        Result settled = evaluate(previous, care, now);
        var updatedCare = CompanionNeglectPolicy.markCaredFor(settled.care(), now);
        var xp = settled.experience();
        return new Result(new Experience(xp.level(), xp.growthXp(),
                xp.totalEarnedXp(), updatedCare.lastEvaluatedAt(), now),
                updatedCare, settled.lostXp());
    }

    /** Apply only XP granted by the server's existing daily-cap policy. */
    public static Experience awardEarnedXp(Experience state, int grantedXp) {
        Objects.requireNonNull(state, "state");
        if (grantedXp < 0 || grantedXp > CompanionGrowthPolicy.DAILY_XP_CAP) {
            throw new IllegalArgumentException("Invalid awarded XP");
        }
        return new Experience(state.level(),
                Math.addExact(state.growthXp(), grantedXp),
                Math.addExact(state.totalEarnedXp(), grantedXp),
                state.lastEvaluatedAt(), state.lastCaredAt());
    }

    /** Advance the stage only after the established evolution checks pass. */
    public static Experience evolve(Experience state,
                                    CompanionNeglectPolicy.Snapshot care,
                                    int distinctCareDays) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(care, "care");
        requireAligned(state, care);
        var evolved = CompanionGrowthPolicy.evolve(state.growthState(care, distinctCareDays));
        return new Experience(evolved.level(), state.growthXp(),
                state.totalEarnedXp(), state.lastEvaluatedAt(), state.lastCaredAt());
    }

    private static void requireAligned(Experience state, CompanionNeglectPolicy.Snapshot care) {
        if (!state.lastEvaluatedAt().equals(care.lastEvaluatedAt())
                || !state.lastCaredAt().equals(care.lastCaredAt())) {
            throw new IllegalArgumentException("Experience and care snapshots are out of sync");
        }
    }

    private static long elapsedDecayDays(Instant lastCaredAt, Instant until) {
        long hours = Duration.between(lastCaredAt, until).toHours();
        return hours <= GRACE_HOURS ? 0L : (hours - GRACE_HOURS) / DECAY_INTERVAL_HOURS;
    }
}
