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
                             Instant lastEvaluatedAt, Instant lastCaredAt,
                             long evaluatedDecayDays) {
        /** Backward-compatible constructor for a new, unprocessed XP snapshot. */
        public Experience(int level, long growthXp, long totalEarnedXp,
                          Instant lastEvaluatedAt, Instant lastCaredAt) {
            this(level, growthXp, totalEarnedXp, lastEvaluatedAt, lastCaredAt, 0L);
        }
        public Experience {
            long floor = floorForLevel(level);
            if (growthXp < floor || totalEarnedXp < growthXp) {
                throw new IllegalArgumentException("Invalid experience or evolution floor");
            }
            Objects.requireNonNull(lastEvaluatedAt, "lastEvaluatedAt");
            Objects.requireNonNull(lastCaredAt, "lastCaredAt");
            if (evaluatedDecayDays < 0) throw new IllegalArgumentException("Negative XP decay cursor");
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
        long firstTick = previous.evaluatedDecayDays();
        // A separate XP-day cursor is required: care timestamps may be offset by
        // minutes relative to the hourly status cursor. Never round the 72h XP
        // boundary back to the previous full hour or charge the same day twice.
        long lastTick = elapsedDecayDays(previous.lastCaredAt(), now);
        if (lastTick < firstTick) {
            throw new IllegalArgumentException("XP decay cursor is ahead of the clock");
        }
        long availableXp = previous.growthXp() - floorForLevel(previous.level());
        long missedDays = lastTick - firstTick;

        // Satiety and cleanliness cannot increase without care, so all normal
        // days precede all dangerous days. Binary-search only the first danger
        // boundary, then use capped arithmetic instead of one loop per day.
        // This keeps multi-year absences bounded by O(log days) meter checks.
        long normalDays = missedDays;
        if (availableXp > 0 && missedDays > 0
                && dangerousAt(care, previous.lastCaredAt(), lastTick)) {
            long left = firstTick + 1;
            long right = lastTick;
            while (left < right) {
                long middle = left + (right - left) / 2;
                if (dangerousAt(care, previous.lastCaredAt(), middle)) {
                    right = middle;
                } else {
                    left = middle + 1;
                }
            }
            normalDays = left - firstTick - 1;
        }
        long normalLoss = cappedLoss(normalDays, NORMAL_DAILY_LOSS, availableXp);
        long dangerLoss = cappedLoss(missedDays - normalDays, DANGER_DAILY_LOSS,
                availableXp - normalLoss);
        long lostXp = normalLoss + dangerLoss;
        long currentXp = previous.growthXp() - lostXp;

        return new Result(new Experience(previous.level(), currentXp,
                previous.totalEarnedXp(), through, previous.lastCaredAt(), lastTick),
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
        return markCaredFor(evaluate(previous, care, now), now);
    }

    static Result markCaredFor(Result settled, Instant now) {
        var updatedCare = CompanionNeglectPolicy.markCaredFor(settled.care(), now);
        var xp = settled.experience();
        return new Result(new Experience(xp.level(), xp.growthXp(),
                xp.totalEarnedXp(), updatedCare.lastEvaluatedAt(), now, 0L),
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
                state.lastEvaluatedAt(), state.lastCaredAt(), state.evaluatedDecayDays());
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
                state.totalEarnedXp(), state.lastEvaluatedAt(), state.lastCaredAt(),
                state.evaluatedDecayDays());
    }

    private static void requireAligned(Experience state, CompanionNeglectPolicy.Snapshot care) {
        if (!state.lastEvaluatedAt().equals(care.lastEvaluatedAt())
                || !state.lastCaredAt().equals(care.lastCaredAt())) {
            throw new IllegalArgumentException("Experience and care snapshots are out of sync");
        }
    }

    private static boolean dangerousAt(CompanionNeglectPolicy.Snapshot care,
                                       Instant lastCaredAt, long tick) {
        long elapsedHours = Math.addExact(GRACE_HOURS,
                Math.multiplyExact(tick, DECAY_INTERVAL_HOURS));
        Instant atTick = lastCaredAt.plus(elapsedHours, ChronoUnit.HOURS);
        var meters = CompanionNeglectPolicy.evaluate(care, atTick).meters();
        return meters.satiety() < CompanionNeglectPolicy.LOW_METER_THRESHOLD
                || meters.cleanliness() < CompanionNeglectPolicy.LOW_METER_THRESHOLD;
    }

    /** Avoid multiplication overflow even for very large legacy XP balances. */
    private static long cappedLoss(long days, int dailyLoss, long availableXp) {
        if (days <= 0 || availableXp <= 0) return 0L;
        if (days > (availableXp - 1) / dailyLoss) return availableXp;
        return days * dailyLoss;
    }

    private static long elapsedDecayDays(Instant lastCaredAt, Instant until) {
        long hours = Duration.between(lastCaredAt, until).toHours();
        return hours <= GRACE_HOURS ? 0L : (hours - GRACE_HOURS) / DECAY_INTERVAL_HOURS;
    }
}
