package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.structure.jigsaw.IrisJigsawPieceRules;

import java.util.Objects;

public record JigsawStudioPieceRules(
        int minimumDepth,
        int maximumDepth,
        int minimumPlacements,
        int maximumPlacements,
        boolean terminal
) {
    public JigsawStudioPieceRules {
        if (minimumDepth < 0 || maximumDepth < minimumDepth || maximumDepth > 30) {
            throw new IllegalArgumentException("Jigsaw Studio piece depth rules are invalid");
        }
        if (minimumPlacements < 0 || maximumPlacements < 0
                || minimumPlacements > 512 || maximumPlacements > 512
                || maximumPlacements != 0 && minimumPlacements > maximumPlacements) {
            throw new IllegalArgumentException("Jigsaw Studio piece placement-count rules are invalid");
        }
    }

    public static JigsawStudioPieceRules from(IrisJigsawPieceRules rules) {
        IrisJigsawPieceRules source = Objects.requireNonNull(rules, "Jigsaw Studio piece rules");
        return new JigsawStudioPieceRules(
                source.getMinimumDepth(),
                source.getMaximumDepth(),
                source.getMinimumPlacements(),
                source.getMaximumPlacements(),
                source.isTerminal());
    }
}
