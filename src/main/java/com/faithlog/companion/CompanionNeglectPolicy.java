package com.faithlog.companion;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Pure lazy decay calculation; caller persists under individual companion row lock. */
public final class CompanionNeglectPolicy {
    private CompanionNeglectPolicy() {}

    public static final int MAX_METER = 100;
    public static final int LOW_METER_THRESHOLD = 25;
    public static final int AFFECTION_GRACE_HOURS = 48;

    public record Meters(int satiety, int cleanliness, int joy, int affection, int health) {
        public Meters {
            for (int meter : new int[]{satiety, cleanliness, joy, affection, health}) {
                if (meter < 0 || meter > MAX_METER) throw new IllegalArgumentException("Meter outside 0..100");
            }
        }
    }

    /**
     * The cursor advances only by completed hours. elapsedHours preserves interval
     * alignment across repeated short queries, and lastCaredAt tracks affection grace.
     */
    public record Snapshot(Meters meters, Instant lastEvaluatedAt, Instant lastCaredAt,
                           long elapsedHours) {
        public Snapshot {
            Objects.requireNonNull(meters);
            Objects.requireNonNull(lastEvaluatedAt);
            Objects.requireNonNull(lastCaredAt);
            if (elapsedHours < 0) throw new IllegalArgumentException("Negative elapsed hours");
        }
        public CompanionGrowthPolicy.State growthState(int level, long growthXp, int careDays) {
            return new CompanionGrowthPolicy.State(level, growthXp, careDays,
                    meters.health(), meters.satiety(), meters.cleanliness());
        }
    }

    /**
     * Satiety -3/4h, cleanliness -2/6h, joy -2/8h, affection -2/day after
     * 48h since care, and health -2/12h when satiety or cleanliness is below 25.
     * XP and evolution levels never decline.
     */
    public static Snapshot evaluate(Snapshot previous, Instant now) {
        Objects.requireNonNull(previous);
        Objects.requireNonNull(now);
        if (now.isBefore(previous.lastEvaluatedAt())) return previous;
        long hours = Duration.between(previous.lastEvaluatedAt(), now).toHours();
        if (hours == 0) return previous;
        long from = previous.elapsedHours();
        long to = Math.addExact(from, hours);
        Meters before = previous.meters();
        int satiety = subtract(before.satiety(), periods(from, to, 4), 3);
        int cleanliness = subtract(before.cleanliness(), periods(from, to, 6), 2);
        int joy = subtract(before.joy(), periods(from, to, 8), 2);

        int health = before.health();
        long healthTicks = periods(from, to, 12);
        long nextTick = Math.addExact(from / 12, 1);
        for (long i = 0; i < healthTicks && health > 0; i++) {
            long tick = Math.multiplyExact(Math.addExact(nextTick, i), 12);
            int satAtTick = subtract(before.satiety(), periods(from, tick, 4), 3);
            int cleanAtTick = subtract(before.cleanliness(), periods(from, tick, 6), 2);
            if (satAtTick < LOW_METER_THRESHOLD || cleanAtTick < LOW_METER_THRESHOLD) {
                health = Math.max(0, health - 2);
            }
        }

        Instant until = previous.lastEvaluatedAt().plus(hours, ChronoUnit.HOURS);
        long affectionTicks = Math.max(0L,
                graceDays(previous.lastCaredAt(), until)
                - graceDays(previous.lastCaredAt(), previous.lastEvaluatedAt()));
        int affection = subtract(before.affection(), affectionTicks, 2);
        return new Snapshot(new Meters(satiety, cleanliness, joy, affection, health),
                until, previous.lastCaredAt(), to);
    }

    /** Apply time decay before any actual care rewards, then reset the neglect grace timer. */
    public static Snapshot markCaredFor(Snapshot previous, Instant now) {
        Objects.requireNonNull(previous);
        Objects.requireNonNull(now);
        if (now.isBefore(previous.lastEvaluatedAt())) {
            throw new IllegalArgumentException("Care time before last evaluation");
        }
        Snapshot updated = evaluate(previous, now);
        return new Snapshot(updated.meters(), updated.lastEvaluatedAt(), now,
                updated.elapsedHours());
    }

    private static long periods(long from, long to, int frequency) {
        return to / frequency - from / frequency;
    }

    private static long graceDays(Instant lastCare, Instant until) {
        long hours = Duration.between(lastCare, until).toHours();
        return hours <= AFFECTION_GRACE_HOURS ? 0L : (hours - AFFECTION_GRACE_HOURS) / 24L;
    }

    private static int subtract(int value, long ticks, int amount) {
        if (ticks >= (value + amount - 1L) / amount) return 0;
        return value - Math.toIntExact(ticks) * amount;
    }
}
