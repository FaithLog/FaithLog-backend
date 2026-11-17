package com.faithlog.companion;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Side-effect-free draft policy for individual companion growth.
 *
 * <p>There is no persistence, endpoint or production gameplay integration here.
 * All decisions must be rechecked in the future server transaction while
 * holding the owned companion's row lock.
 */
public final class CompanionGrowthPolicy {
    private CompanionGrowthPolicy() {}

    public static final int MAX_LEVEL = 3;
    public static final int DAILY_XP_CAP = 100;

    public enum Blocker {
        MAX_LEVEL, EXPERIENCE, CARE_DAYS, HEALTH, SATIETY, CLEANLINESS
    }

    /**
     * The growth counter never decreases. Temporary care meters may decrease.
     * A stage is 1, 2 or 3; not the number of times evolved (0, 1, 2).
     */
    public record State(int level, long growthXp, int distinctCareDays,
                        int health, int satiety, int cleanliness) {
        public State {
            if (level < 1 || level > MAX_LEVEL || growthXp < 0 || distinctCareDays < 0) {
                throw new IllegalArgumentException("Invalid level, experience or care day count");
            }
            validateMeter(health);
            validateMeter(satiety);
            validateMeter(cleanliness);
        }

        private static void validateMeter(int meter) {
            if (meter < 0 || meter > 100) {
                throw new IllegalArgumentException("Care meter must be 0..100");
            }
        }
    }

    public record Threshold(long growthXp, int distinctCareDays, int health,
                            int satiety, int cleanliness) {}

    public record Evaluation(int currentLevel, int nextLevel,
                             Threshold threshold, List<Blocker> blockers) {
        public Evaluation {
            blockers = List.copyOf(blockers);
        }
        public boolean canEvolve() {
            return blockers.isEmpty() && currentLevel < MAX_LEVEL;
        }
    }

    public static Threshold thresholdForNextLevel(int currentLevel) {
        return switch (currentLevel) {
            case 1 -> new Threshold(300L, 3, 60, 50, 50);
            case 2 -> new Threshold(1000L, 10, 70, 60, 60);
            default -> throw new IllegalArgumentException("No next level");
        };
    }

    public static Evaluation evaluate(State state) {
        Objects.requireNonNull(state, "state");
        if (state.level() == MAX_LEVEL) {
            return new Evaluation(MAX_LEVEL, MAX_LEVEL, null, List.of(Blocker.MAX_LEVEL));
        }

        Threshold threshold = thresholdForNextLevel(state.level());
        List<Blocker> blockers = new ArrayList<>();
        if (state.growthXp() < threshold.growthXp()) blockers.add(Blocker.EXPERIENCE);
        if (state.distinctCareDays() < threshold.distinctCareDays()) blockers.add(Blocker.CARE_DAYS);
        if (state.health() < threshold.health()) blockers.add(Blocker.HEALTH);
        if (state.satiety() < threshold.satiety()) blockers.add(Blocker.SATIETY);
        if (state.cleanliness() < threshold.cleanliness()) blockers.add(Blocker.CLEANLINESS);

        return new Evaluation(state.level(), state.level() + 1, threshold, blockers);
    }

    /** Progression is explicit and retains accumulated experience and current care meters. */
    public static State evolve(State state) {
        if (!evaluate(state).canEvolve()) {
            throw new IllegalStateException("Evolution requirements not met");
        }
        return new State(state.level() + 1, state.growthXp(),
                state.distinctCareDays(), state.health(), state.satiety(), state.cleanliness());
    }

    public enum CareAction {
        FEED(15, Duration.ofHours(4)),
        PET(10, Duration.ofHours(1)),
        PLAY(20, Duration.ofHours(2)),
        CLEAN(15, Duration.ofHours(3)),
        SLEEP(0, Duration.ZERO);

        private final int xp;
        private final Duration cooldown;

        CareAction(int xp, Duration cooldown) {
            this.xp = xp;
            this.cooldown = cooldown;
        }

        public int xp() {
            return xp;
        }

        public Duration cooldown() {
            return cooldown;
        }
    }

    /** At most 100 growth XP per Korea-calendar day, regardless of button tapping. */
    public static int growthXpAward(int earnedToday, CareAction action) {
        Objects.requireNonNull(action, "action");
        if (earnedToday < 0 || earnedToday > DAILY_XP_CAP) {
            throw new IllegalArgumentException("Invalid daily experience");
        }
        return Math.min(action.xp(), DAILY_XP_CAP - earnedToday);
    }

    /** Pure temporal check. An action endpoint must also enforce an idempotency key. */
    public static boolean cooldownElapsed(Instant now, Instant lastUsed, CareAction action) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(action, "action");
        if (lastUsed == null) return true;
        if (now.isBefore(lastUsed)) return false;
        return !now.isBefore(lastUsed.plus(action.cooldown()));
    }
}
