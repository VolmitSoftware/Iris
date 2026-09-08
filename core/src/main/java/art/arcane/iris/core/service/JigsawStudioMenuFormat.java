package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPieceRules;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.UIElement;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static art.arcane.iris.core.service.JigsawStudioMenuController.PLACEMENT_RULE_SHIFT_STEP;
import static art.arcane.iris.core.service.JigsawStudioMenuController.RULE_SHIFT_STEP;

final class JigsawStudioMenuFormat {
    private JigsawStudioMenuFormat() {
    }

    static boolean hasMembership(
            JigsawStudioMenuState state,
            String workcellId,
            JigsawStudioMenuState.Membership expected
    ) {
        JigsawStudioMenuState.Variant active = activeVariant(state, workcellId);
        if (active == null) {
            return false;
        }
        for (JigsawStudioMenuState.Membership membership : active.memberships()) {
            if (membership.poolKey().equals(expected.poolKey())
                    && membership.entryIndex() == expected.entryIndex()) {
                return true;
            }
        }
        return false;
    }

    static JigsawStudioMenuState.Variant activeVariant(
            JigsawStudioMenuState state,
            String workcellId
    ) {
        JigsawStudioMenuState.Workcell workcell = state.workcell(workcellId);
        return workcell == null ? null : workcell.activeVariant();
    }

    static JigsawStudioMenuState.Variant variant(
            JigsawStudioMenuState.Workcell workcell,
            String pieceKey
    ) {
        if (workcell == null || pieceKey == null) {
            return null;
        }
        for (JigsawStudioMenuState.Variant variant : workcell.variants()) {
            if (variant.pieceKey().equals(pieceKey)) {
                return variant;
            }
        }
        return null;
    }

    static String nextThemeSetKey(List<JigsawStudioMenuState.ThemeSet> themeSets) {
        List<JigsawStudioMenuState.ThemeSet> values = List.copyOf(Objects.requireNonNull(
                themeSets,
                "Jigsaw Studio theme sets"));
        int candidate = 1;
        while (candidate > 0) {
            String key = "variant-" + candidate;
            boolean present = false;
            for (JigsawStudioMenuState.ThemeSet themeSet : values) {
                if (Objects.requireNonNull(themeSet, "Jigsaw Studio theme set").key().equals(key)) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                return key;
            }
            candidate = Math.incrementExact(candidate);
        }
        throw new IllegalStateException("Jigsaw Studio cannot allocate another numbered theme set");
    }

    static String themeSelectionPercent(
            List<JigsawStudioMenuState.ThemeSet> themeSets,
            JigsawStudioMenuState.ThemeSet target
    ) {
        List<JigsawStudioMenuState.ThemeSet> activeThemeSets = Objects.requireNonNull(
                themeSets,
                "Jigsaw Studio theme sets");
        JigsawStudioMenuState.ThemeSet activeTarget = Objects.requireNonNull(
                target,
                "Jigsaw Studio target theme set");
        int totalWeight = 0;
        for (JigsawStudioMenuState.ThemeSet themeSet : activeThemeSets) {
            totalWeight = Math.addExact(totalWeight, Objects.requireNonNull(
                    themeSet,
                    "Jigsaw Studio theme set").weight());
        }
        if (totalWeight < 1) {
            return "0.0%";
        }
        return String.format(
                Locale.ROOT,
                "%.1f%%",
                activeTarget.weight() * 100.0D / totalWeight);
    }

    static Optional<Integer> adjustedPositiveValue(int value, int delta) {
        if (value < 1 || delta == 0) {
            throw new IllegalArgumentException("Jigsaw Studio positive value adjustment is invalid");
        }
        try {
            int adjusted = Math.addExact(value, delta);
            return adjusted < 1 ? Optional.empty() : Optional.of(adjusted);
        } catch (ArithmeticException exception) {
            return Optional.empty();
        }
    }

    static Optional<JigsawStudioPieceRules> adjustedRules(
            JigsawStudioPieceRules rules,
            RuleField field,
            int delta
    ) {
        JigsawStudioPieceRules current = Objects.requireNonNull(rules, "Jigsaw Studio piece rules");
        RuleField target = Objects.requireNonNull(field, "Jigsaw Studio piece rule field");
        if (delta == 0) {
            throw new IllegalArgumentException("Jigsaw Studio piece rule delta cannot be zero");
        }
        try {
            int minimumDepth = current.minimumDepth();
            int maximumDepth = current.maximumDepth();
            int minimumPlacements = current.minimumPlacements();
            int maximumPlacements = current.maximumPlacements();
            switch (target) {
                case MINIMUM_DEPTH -> minimumDepth = Math.addExact(minimumDepth, delta);
                case MAXIMUM_DEPTH -> maximumDepth = Math.addExact(maximumDepth, delta);
                case MINIMUM_PLACEMENTS -> minimumPlacements = Math.addExact(minimumPlacements, delta);
                case MAXIMUM_PLACEMENTS -> {
                    Optional<Integer> adjustedMaximum = adjustedMaximumPlacements(maximumPlacements, delta);
                    if (adjustedMaximum.isEmpty()) {
                        return Optional.empty();
                    }
                    maximumPlacements = adjustedMaximum.get();
                }
            }
            return Optional.of(new JigsawStudioPieceRules(
                    minimumDepth,
                    maximumDepth,
                    minimumPlacements,
                    maximumPlacements,
                    current.terminal()));
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    static JigsawStudioPieceRules withTerminal(JigsawStudioPieceRules rules, boolean terminal) {
        JigsawStudioPieceRules current = Objects.requireNonNull(rules, "Jigsaw Studio piece rules");
        return new JigsawStudioPieceRules(
                current.minimumDepth(),
                current.maximumDepth(),
                current.minimumPlacements(),
                current.maximumPlacements(),
                terminal);
    }

    private static Optional<Integer> adjustedMaximumPlacements(int value, int delta) {
        if (value == 0) {
            if (delta > 0) {
                return Optional.empty();
            }
            int adjusted = Math.addExact(513, delta);
            return adjusted < 1 ? Optional.empty() : Optional.of(adjusted);
        }
        int adjusted = Math.addExact(value, delta);
        if (adjusted > 512) {
            return Optional.of(0);
        }
        return adjusted < 1 ? Optional.empty() : Optional.of(adjusted);
    }

    static Optional<JigsawStudioCellDimensions> adjustedDimensions(
            JigsawStudioCellDimensions dimensions,
            DimensionAxis axis,
            int delta
    ) {
        JigsawStudioCellDimensions current = Objects.requireNonNull(
                dimensions,
                "Jigsaw Studio workcell dimensions");
        DimensionAxis target = Objects.requireNonNull(axis, "Jigsaw Studio workcell dimension axis");
        if (delta == 0) {
            throw new IllegalArgumentException("Jigsaw Studio workcell dimension delta cannot be zero");
        }
        try {
            int width = target == DimensionAxis.WIDTH
                    ? Math.addExact(current.width(), delta)
                    : current.width();
            int height = target == DimensionAxis.HEIGHT
                    ? Math.addExact(current.height(), delta)
                    : current.height();
            int depth = target == DimensionAxis.DEPTH
                    ? Math.addExact(current.depth(), delta)
                    : current.depth();
            return Optional.of(new JigsawStudioCellDimensions(width, height, depth));
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    static JigsawStudioMenuState.Workcell withCapacity(
            JigsawStudioMenuState.Workcell workcell,
            JigsawStudioCellDimensions capacity
    ) {
        JigsawStudioMenuState.Workcell source = Objects.requireNonNull(
                workcell,
                "Jigsaw Studio menu workcell");
        return new JigsawStudioMenuState.Workcell(
                source.stableId(),
                source.canonicalName(),
                source.displayName(),
                Objects.requireNonNull(capacity, "Jigsaw Studio staged workcell capacity"),
                source.enabled(),
                source.activeVariantKey(),
                source.dirty(),
                source.saving(),
                source.loading(),
                source.connectorsVisible(),
                source.variants());
    }

    static int pageCount(int itemCount, int pageSize) {
        if (itemCount < 0 || pageSize < 1) {
            throw new IllegalArgumentException("Jigsaw Studio menu page bounds are invalid");
        }
        return Math.max(1, (itemCount + pageSize - 1) / pageSize);
    }

    static int clampPage(int requestedPage, int itemCount, int pageSize) {
        int maximum = pageCount(itemCount, pageSize) - 1;
        return Math.max(0, Math.min(requestedPage, maximum));
    }

    static <T> List<T> page(List<T> items, int requestedPage, int pageSize) {
        List<T> values = List.copyOf(Objects.requireNonNull(items, "Jigsaw Studio menu page items"));
        int page = clampPage(requestedPage, values.size(), pageSize);
        int start = page * pageSize;
        int end = Math.min(values.size(), start + pageSize);
        return values.subList(start, end);
    }

    static UIElement evaluationElement(JigsawStudioMenuState.Evaluation evaluation) {
        UIElement element = element(
                "evaluation",
                evaluationMaterial(evaluation.state()),
                evaluationColor(evaluation.state()) + evaluationName(evaluation.state()));
        element.addLore(ChatColor.GRAY + "Evaluation updates automatically.");
        element.addLore(ChatColor.GRAY + "Seed: " + evaluation.seed());
        if (evaluation.generation() > 0L) {
            element.addLore(ChatColor.GRAY + "Generation: " + evaluation.generation());
        }
        if (!evaluation.selectedTheme().isEmpty()) {
            element.addLore(ChatColor.GRAY + "Theme: " + safe(evaluation.selectedTheme()));
        }
        element.addLore(ChatColor.GRAY + "Pieces: " + evaluation.pieceCount());
        if (!evaluation.detail().isEmpty()) {
            element.addLore(ChatColor.GRAY + safe(evaluation.detail()));
        }
        return element;
    }

    static Material evaluationMaterial(JigsawStudioEvaluationState state) {
        return switch (state) {
            case PENDING -> Material.CLOCK;
            case VALID -> Material.EMERALD;
            case WARNING -> Material.YELLOW_DYE;
            case INVALID -> Material.RED_DYE;
            case STALE -> Material.GRAY_DYE;
        };
    }

    private static ChatColor evaluationColor(JigsawStudioEvaluationState state) {
        return switch (state) {
            case PENDING -> ChatColor.AQUA;
            case VALID -> ChatColor.GREEN;
            case WARNING -> ChatColor.YELLOW;
            case INVALID -> ChatColor.RED;
            case STALE -> ChatColor.GRAY;
        };
    }

    private static String evaluationName(JigsawStudioEvaluationState state) {
        return switch (state) {
            case PENDING -> "Evaluation Pending";
            case VALID -> "Ready to Assemble";
            case WARNING -> "Ready with Warnings";
            case INVALID -> "Assembly Blocked";
            case STALE -> "Evaluation Stale";
        };
    }

    static Material workcellMaterial(JigsawStudioMenuState.Workcell workcell, boolean selected) {
        if (!workcell.enabled()) {
            return Material.GRAY_WOOL;
        }
        if (workcell.loading() || workcell.saving()) {
            return Material.YELLOW_WOOL;
        }
        if (workcell.dirty()) {
            return Material.ORANGE_WOOL;
        }
        return selected ? Material.LIME_WOOL : Material.LIGHT_GRAY_WOOL;
    }

    static Material variantMaterial(JigsawStudioMenuState.Variant variant) {
        if (variant.active()) {
            return Material.JIGSAW;
        }
        return variant.owned() ? Material.PAPER : Material.GRAY_DYE;
    }

    static String workcellStatus(JigsawStudioMenuState.Workcell workcell) {
        if (workcell.loading()) {
            return ChatColor.YELLOW + "Loading variant";
        }
        if (workcell.saving()) {
            return ChatColor.YELLOW + "Autosave in progress";
        }
        if (workcell.dirty()) {
            return ChatColor.GOLD + "Autosave pending";
        }
        return ChatColor.GREEN + "Autosaved";
    }

    static String dimensions(JigsawStudioCellDimensions dimensions) {
        return dimensions.width() + "x" + dimensions.height() + "x" + dimensions.depth();
    }

    static String themes(List<String> themes) {
        return themes.isEmpty() ? "None" : safe(String.join(", ", themes));
    }

    static String maximumPlacements(int maximum) {
        return maximum == 0 ? "Unlimited" : Integer.toString(maximum);
    }

    static String chance(double chance) {
        return String.format(Locale.ROOT, "%.1f%%", chance * 100D);
    }

    static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    static String compatibilityName(JigsawStudioCompatibilityTarget target) {
        return switch (target) {
            case IRIS_EXTENDED -> "Iris Extended";
            case VANILLA_PORTABLE -> "Vanilla Portable";
        };
    }

    static UIElement element(String id, Material material, String name) {
        return new UIElement(id)
                .setMaterial(new MaterialBlock(material))
                .setName(name);
    }

    static String title(String structureKey) {
        String value = "Iris Jigsaw: " + safe(displayKey(structureKey));
        return value.length() > 32 ? value.substring(0, 32) : value;
    }

    static String displayKey(String resourceKey) {
        int separator = resourceKey.lastIndexOf('/');
        return separator < 0 ? resourceKey : resourceKey.substring(separator + 1);
    }

    static String safe(String value) {
        return BoardSVC.untrustedBoardValue(value);
    }

    enum RuleField {
        MINIMUM_DEPTH("Minimum Depth", Material.LIGHT_BLUE_DYE, RULE_SHIFT_STEP) {
            @Override
            int value(JigsawStudioPieceRules rules) {
                return rules.minimumDepth();
            }
        },
        MAXIMUM_DEPTH("Maximum Depth", Material.BLUE_DYE, RULE_SHIFT_STEP) {
            @Override
            int value(JigsawStudioPieceRules rules) {
                return rules.maximumDepth();
            }
        },
        MINIMUM_PLACEMENTS("Minimum Placements", Material.TARGET, PLACEMENT_RULE_SHIFT_STEP) {
            @Override
            int value(JigsawStudioPieceRules rules) {
                return rules.minimumPlacements();
            }
        },
        MAXIMUM_PLACEMENTS("Maximum Placements", Material.GREEN_DYE, PLACEMENT_RULE_SHIFT_STEP) {
            @Override
            int value(JigsawStudioPieceRules rules) {
                return rules.maximumPlacements();
            }

            @Override
            String displayValue(int value) {
                return maximumPlacements(value);
            }
        };

        private final String displayName;
        private final Material material;
        private final int shiftStep;

        RuleField(String displayName, Material material, int shiftStep) {
            this.displayName = displayName;
            this.material = material;
            this.shiftStep = shiftStep;
        }

        String displayName() {
            return displayName;
        }

        Material material() {
            return material;
        }

        int shiftStep() {
            return shiftStep;
        }

        String displayValue(int value) {
            return Integer.toString(value);
        }

        abstract int value(JigsawStudioPieceRules rules);
    }

    enum DimensionAxis {
        WIDTH("Width") {
            @Override
            int value(JigsawStudioCellDimensions dimensions) {
                return dimensions.width();
            }
        },
        HEIGHT("Height") {
            @Override
            int value(JigsawStudioCellDimensions dimensions) {
                return dimensions.height();
            }
        },
        DEPTH("Depth") {
            @Override
            int value(JigsawStudioCellDimensions dimensions) {
                return dimensions.depth();
            }
        };

        private final String displayName;

        DimensionAxis(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }

        abstract int value(JigsawStudioCellDimensions dimensions);
    }
}
