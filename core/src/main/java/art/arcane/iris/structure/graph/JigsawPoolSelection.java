package art.arcane.iris.structure.graph;

import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceRules;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JigsawPoolSelection {
    private JigsawPoolSelection() {
    }

    public static String directFallbackKey(IrisJigsawPool pool) {
        Objects.requireNonNull(pool);
        return normalize(pool.getFallback());
    }

    public static List<String> candidatePoolKeys(
            String primaryKey,
            IrisJigsawPool primaryPool,
            boolean includePrimary
    ) {
        Objects.requireNonNull(primaryPool);
        String normalizedPrimary = normalize(primaryKey);
        String directFallback = directFallbackKey(primaryPool);
        if (!includePrimary) {
            return directFallback.isEmpty() ? List.of() : List.of(directFallback);
        }
        if (normalizedPrimary.isEmpty()) {
            return List.of();
        }
        return directFallback.isEmpty()
                ? List.of(normalizedPrimary)
                : List.of(normalizedPrimary, directFallback);
    }

    public static String selectTheme(IrisStructure structure, RNG rng) {
        IrisStructure activeStructure = Objects.requireNonNull(structure);
        RNG activeRng = Objects.requireNonNull(rng);
        if (activeStructure.getThemeSets() == null || activeStructure.getThemeSets().isEmpty()) {
            return "";
        }
        List<IrisJigsawThemeSet> themes = new ArrayList<>(activeStructure.getThemeSets().size());
        long totalWeight = 0L;
        for (IrisJigsawThemeSet theme : activeStructure.getThemeSets()) {
            if (theme == null || normalize(theme.getKey()).isEmpty() || theme.getWeight() <= 0) {
                throw new IllegalArgumentException("Jigsaw theme sets require non-blank keys and positive weights");
            }
            themes.add(theme);
            totalWeight = Math.addExact(totalWeight, theme.getWeight());
        }
        if (themes.size() == 1) {
            return normalize(themes.getFirst().getKey());
        }
        long target = activeRng.nextLong(totalWeight);
        for (IrisJigsawThemeSet theme : themes) {
            target -= theme.getWeight();
            if (target < 0L) {
                return normalize(theme.getKey());
            }
        }
        return normalize(themes.getLast().getKey());
    }

    public static boolean passesChance(IrisJigsawPieceEntry entry, RNG rng) {
        IrisJigsawPieceEntry activeEntry = Objects.requireNonNull(entry);
        RNG activeRng = Objects.requireNonNull(rng);
        double chance = activeEntry.getChance();
        if (!Double.isFinite(chance) || chance < 0D || chance > 1D) {
            throw new IllegalArgumentException("Jigsaw pool membership chance must be finite and within 0 and 1");
        }
        if (!activeEntry.requiresChanceRoll()) {
            return chance >= 1D;
        }
        return activeEntry.passesChance(activeRng.nextDouble());
    }

    public static boolean pieceEligible(
            IrisJigsawPiece piece,
            String selectedTheme,
            int depth,
            int existingPlacements
    ) {
        IrisJigsawPiece activePiece = Objects.requireNonNull(piece);
        IrisJigsawPieceRules rules = activePiece.resolvedRules();
        return activePiece.supportsTheme(normalize(selectedTheme))
                && rules.allowsDepth(depth)
                && rules.allowsPlacement(existingPlacements);
    }

    public static boolean needsMinimumPlacement(IrisJigsawPiece piece, int existingPlacements) {
        return Objects.requireNonNull(piece).resolvedRules().requiresMorePlacements(existingPlacements);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
