package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveConflictPolicy;
import art.arcane.iris.generation.hydrology.cave.HydrologyCavePlan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

final class HydrologyCrossTileCaveAdmission {
    private static final Comparator<Claim> CLAIM_PRIORITY = Comparator
            .comparing((Claim claim) -> claim.plan().source(), HydrologyCaveConflictPolicy.sourcePriority())
            .thenComparing(Claim::profileKey);
    private static final Comparator<RankedClaim> BLOCKER_PRIORITY = Comparator
            .comparingInt(RankedClaim::ownerRank)
            .thenComparing(RankedClaim::claim, CLAIM_PRIORITY)
            .thenComparing(RankedClaim::ownerKey);

    private HydrologyCrossTileCaveAdmission() {
    }

    static Result admit(
            int currentOwnerRank,
            Collection<Claim> currentClaims,
            Collection<RankedClaim> blockers
    ) {
        if (currentOwnerRank < 0) {
            throw new IllegalArgumentException("currentOwnerRank cannot be negative");
        }
        ArrayList<Claim> orderedClaims = new ArrayList<>(Objects.requireNonNull(currentClaims, "currentClaims"));
        orderedClaims.sort(CLAIM_PRIORITY);
        ArrayList<RankedClaim> orderedBlockers = new ArrayList<>(Objects.requireNonNull(blockers, "blockers"));
        orderedBlockers.sort(BLOCKER_PRIORITY);
        for (RankedClaim blocker : orderedBlockers) {
            if (blocker.ownerRank() >= currentOwnerRank) {
                throw new IllegalArgumentException("Cross-tile blockers must have a lower owner color rank");
            }
        }

        ArrayList<Rejection> rejections = new ArrayList<>();
        for (Claim claim : orderedClaims) {
            RankedClaim winner = null;
            for (RankedClaim blocker : orderedBlockers) {
                if (claim.courseId() == blocker.claim().courseId()) {
                    throw new IllegalArgumentException("A cave course cannot belong to multiple color-ranked owners");
                }
                if (!conflicts(claim, blocker.claim())) {
                    continue;
                }
                if (winner == null || BLOCKER_PRIORITY.compare(blocker, winner) < 0) {
                    winner = blocker;
                }
            }
            if (winner != null) {
                rejections.add(new Rejection(claim, winner));
            }
        }
        return new Result(rejections);
    }

    private static boolean conflicts(Claim left, Claim right) {
        return HydrologyCaveConflictPolicy.hasIncompatibleOverlap(
                left.profileKey(),
                left.plan().actions(),
                right.profileKey(),
                right.plan().actions()
        );
    }

    record Claim(String profileKey, HydrologyCavePlan plan) {
        Claim {
            if (profileKey == null || profileKey.isBlank()) {
                throw new IllegalArgumentException("profileKey must not be blank");
            }
            profileKey = profileKey.trim();
            Objects.requireNonNull(plan, "plan");
            if (!plan.accepted()) {
                throw new IllegalArgumentException("Cross-tile admission requires accepted cave plans");
            }
        }

        long courseId() {
            return plan.source().sourceId();
        }
    }

    record RankedClaim(HydrologyTileKey ownerKey, int ownerRank, Claim claim) {
        RankedClaim {
            Objects.requireNonNull(ownerKey, "ownerKey");
            if (ownerRank < 0) {
                throw new IllegalArgumentException("ownerRank cannot be negative");
            }
            Objects.requireNonNull(claim, "claim");
        }
    }

    record Result(List<Rejection> rejections) {
        Result {
            rejections = List.copyOf(Objects.requireNonNull(rejections, "rejections"));
        }

        Map<Long, Long> loserCourseIdsToWinnerSourceIds() {
            TreeMap<Long, Long> ordered = new TreeMap<>();
            for (Rejection rejection : rejections) {
                ordered.put(rejection.loser().courseId(), rejection.winnerSourceId());
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(ordered));
        }

        Rejection rejection(Claim claim) {
            Objects.requireNonNull(claim, "claim");
            for (Rejection rejection : rejections) {
                if (rejection.loser().equals(claim)) {
                    return rejection;
                }
            }
            return null;
        }
    }

    record Rejection(Claim loser, RankedClaim winner) {
        Rejection {
            Objects.requireNonNull(loser, "loser");
            Objects.requireNonNull(winner, "winner");
        }

        long winnerSourceId() {
            return winner.claim().plan().source().sourceId();
        }
    }
}
