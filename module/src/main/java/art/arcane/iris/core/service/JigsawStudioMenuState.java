package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPieceRules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record JigsawStudioMenuState(
        UUID worldId,
        UUID requestId,
        String structureKey,
        JigsawStudioMode mode,
        JigsawStudioCompatibilityTarget compatibilityTarget,
        boolean requireCaps,
        List<ThemeSet> themeSets,
        String selectedWorkcellId,
        Evaluation evaluation,
        List<Workcell> workcells
) {
    public JigsawStudioMenuState {
        worldId = Objects.requireNonNull(worldId, "Jigsaw Studio menu world ID");
        requestId = Objects.requireNonNull(requestId, "Jigsaw Studio menu request ID");
        structureKey = requireText(structureKey, "structure key");
        mode = Objects.requireNonNull(mode, "Jigsaw Studio menu mode");
        compatibilityTarget = Objects.requireNonNull(
                compatibilityTarget,
                "Jigsaw Studio menu compatibility target");
        themeSets = List.copyOf(Objects.requireNonNull(themeSets, "Jigsaw Studio menu theme sets"));
        selectedWorkcellId = optionalText(selectedWorkcellId);
        evaluation = Objects.requireNonNull(evaluation, "Jigsaw Studio menu evaluation");
        workcells = List.copyOf(Objects.requireNonNull(workcells, "Jigsaw Studio menu workcells"));

        Set<String> workcellIds = new HashSet<>();
        for (Workcell workcell : workcells) {
            Workcell resolved = Objects.requireNonNull(workcell, "Jigsaw Studio menu workcell");
            if (!workcellIds.add(resolved.stableId())) {
                throw new IllegalArgumentException("Duplicate Jigsaw Studio menu workcell " + resolved.stableId());
            }
        }
        if (!selectedWorkcellId.isEmpty() && !workcellIds.contains(selectedWorkcellId)) {
            throw new IllegalArgumentException("Selected Jigsaw Studio menu workcell is not present");
        }

        Set<String> themeKeys = new HashSet<>();
        for (ThemeSet themeSet : themeSets) {
            ThemeSet resolved = Objects.requireNonNull(themeSet, "Jigsaw Studio menu theme set");
            if (!themeKeys.add(resolved.key())) {
                throw new IllegalArgumentException("Duplicate Jigsaw Studio menu theme " + resolved.key());
            }
        }
    }

    public Workcell selectedWorkcell() {
        if (selectedWorkcellId.isEmpty()) {
            return null;
        }
        return workcell(selectedWorkcellId);
    }

    public boolean irisExtended() {
        return compatibilityTarget == JigsawStudioCompatibilityTarget.IRIS_EXTENDED;
    }

    public Workcell workcell(String stableId) {
        if (stableId == null) {
            return null;
        }
        for (Workcell workcell : workcells) {
            if (workcell.stableId().equals(stableId)) {
                return workcell;
            }
        }
        return null;
    }

    public ThemeSet themeSet(String key) {
        if (key == null) {
            return null;
        }
        for (ThemeSet themeSet : themeSets) {
            if (themeSet.key().equals(key)) {
                return themeSet;
            }
        }
        return null;
    }

    public record ThemeSet(String key, int weight) {
        public ThemeSet {
            key = requireText(key, "theme key");
            if (weight < 1) {
                throw new IllegalArgumentException("Jigsaw Studio menu theme weight must be positive");
            }
        }
    }

    public record Evaluation(
            JigsawStudioEvaluationState state,
            long generation,
            long seed,
            String selectedTheme,
            int pieceCount,
            String detail
    ) {
        public Evaluation {
            state = Objects.requireNonNull(state, "Jigsaw Studio evaluation state");
            if (generation < 0L) {
                throw new IllegalArgumentException("Jigsaw Studio evaluation generation cannot be negative");
            }
            selectedTheme = optionalText(selectedTheme);
            if (pieceCount < 0) {
                throw new IllegalArgumentException("Jigsaw Studio evaluation piece count cannot be negative");
            }
            detail = optionalText(detail);
        }

        public static Evaluation pending() {
            return new Evaluation(
                    JigsawStudioEvaluationState.PENDING,
                    0L,
                    1337L,
                    "",
                    0,
                    "Iris evaluates the graph automatically as authoring state changes.");
        }

        public static Evaluation from(JigsawStudioGraphEvaluation evaluation) {
            JigsawStudioGraphEvaluation source = Objects.requireNonNull(
                    evaluation,
                    "Jigsaw Studio graph evaluation");
            return new Evaluation(
                    source.state(),
                    source.generation(),
                    source.seed(),
                    source.selectedTheme(),
                    source.pieceCount(),
                    source.detail());
        }
    }

    public record Workcell(
            String stableId,
            String canonicalName,
            String displayName,
            JigsawStudioCellDimensions capacity,
            boolean enabled,
            String activeVariantKey,
            boolean dirty,
            boolean saving,
            boolean loading,
            boolean connectorsVisible,
            List<Variant> variants
    ) {
        public Workcell {
            stableId = requireText(stableId, "workcell ID");
            canonicalName = requireText(canonicalName, "workcell canonical name");
            displayName = requireText(displayName, "workcell display name");
            capacity = Objects.requireNonNull(capacity, "Jigsaw Studio menu workcell capacity");
            activeVariantKey = optionalText(activeVariantKey);
            variants = List.copyOf(Objects.requireNonNull(variants, "Jigsaw Studio menu variants"));

            Set<String> variantKeys = new HashSet<>();
            int activeVariants = 0;
            for (Variant variant : variants) {
                Variant resolved = Objects.requireNonNull(variant, "Jigsaw Studio menu variant");
                if (!variantKeys.add(resolved.pieceKey())) {
                    throw new IllegalArgumentException("Duplicate Jigsaw Studio menu variant " + resolved.pieceKey());
                }
                if (resolved.active()) {
                    activeVariants++;
                    if (!resolved.pieceKey().equals(activeVariantKey)) {
                        throw new IllegalArgumentException("Active Jigsaw Studio menu variant key does not match");
                    }
                }
            }
            if ((activeVariantKey.isEmpty() && activeVariants != 0)
                    || (!activeVariantKey.isEmpty() && activeVariants != 1)) {
                throw new IllegalArgumentException("Jigsaw Studio menu workcell active variant is inconsistent");
            }
        }

        public Variant activeVariant() {
            if (activeVariantKey.isEmpty()) {
                return null;
            }
            for (Variant variant : variants) {
                if (variant.pieceKey().equals(activeVariantKey)) {
                    return variant;
                }
            }
            return null;
        }

        public boolean busy() {
            return saving || loading;
        }
    }

    public record Variant(
            String pieceKey,
            String displayName,
            Optional<JigsawStudioCellDimensions> dimensions,
            boolean active,
            boolean owned,
            boolean rotatable,
            boolean rotationEditable,
            boolean resizableToCapacity,
            List<String> themes,
            JigsawStudioPieceRules rules,
            List<Membership> memberships
    ) {
        public Variant {
            pieceKey = requireText(pieceKey, "variant piece key");
            displayName = requireText(displayName, "variant display name");
            dimensions = Objects.requireNonNull(dimensions, "Jigsaw Studio menu variant dimensions");
            List<String> resolvedThemes = new ArrayList<>();
            Set<String> uniqueThemes = new HashSet<>();
            for (String theme : Objects.requireNonNull(themes, "Jigsaw Studio menu variant themes")) {
                String resolvedTheme = requireText(theme, "variant theme");
                if (!uniqueThemes.add(resolvedTheme)) {
                    throw new IllegalArgumentException("Duplicate Jigsaw Studio menu variant theme "
                            + resolvedTheme);
                }
                resolvedThemes.add(resolvedTheme);
            }
            themes = List.copyOf(resolvedThemes);
            rules = Objects.requireNonNull(rules, "Jigsaw Studio menu variant rules");
            memberships = List.copyOf(Objects.requireNonNull(
                    memberships,
                    "Jigsaw Studio menu variant memberships"));
            if (rotationEditable && !owned) {
                throw new IllegalArgumentException("Read-only Jigsaw Studio variants cannot edit rotation");
            }
            if (resizableToCapacity && (!owned || !active)) {
                throw new IllegalArgumentException(
                        "Only the active owned Jigsaw Studio variant can resize to its workcell capacity");
            }

            Set<MembershipIdentity> identities = new HashSet<>();
            for (Membership membership : memberships) {
                Membership resolved = Objects.requireNonNull(
                        membership,
                        "Jigsaw Studio menu variant membership");
                MembershipIdentity identity = new MembershipIdentity(resolved.poolKey(), resolved.entryIndex());
                if (!identities.add(identity)) {
                    throw new IllegalArgumentException("Duplicate Jigsaw Studio menu membership "
                            + resolved.poolKey() + "[" + resolved.entryIndex() + "]");
                }
            }
        }
    }

    public record Membership(String poolKey, int entryIndex, int weight, double chance) {
        public Membership {
            poolKey = requireText(poolKey, "membership pool key");
            if (entryIndex < 0) {
                throw new IllegalArgumentException("Jigsaw Studio menu membership index cannot be negative");
            }
            if (weight < 1) {
                throw new IllegalArgumentException("Jigsaw Studio menu membership weight must be positive");
            }
            if (!Double.isFinite(chance) || chance < 0D || chance > 1D) {
                throw new IllegalArgumentException("Jigsaw Studio menu membership chance must be within 0 and 1");
            }
        }
    }

    private record MembershipIdentity(String poolKey, int entryIndex) {
    }

    private static String requireText(String value, String name) {
        String normalized = optionalText(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Jigsaw Studio menu " + name + " cannot be blank");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        return value == null ? "" : value.trim();
    }
}
