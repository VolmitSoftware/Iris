package art.arcane.iris.engine.object.structure.jigsaw;

import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Deterministic eligibility, placement-count, and terminal-role rules for one jigsaw piece.")
@Data
public class IrisJigsawPieceRules {
    @MinNumber(0)
    @MaxNumber(30)
    @Desc("The shallowest assembly depth at which this piece may be placed. The start piece is depth zero.")
    private int minimumDepth = 0;

    @MinNumber(0)
    @MaxNumber(30)
    @Desc("The deepest assembly depth at which this piece may be placed.")
    private int maximumDepth = 30;

    @MinNumber(0)
    @MaxNumber(512)
    @Desc("The minimum number of placements required for this piece in a completed assembly.")
    private int minimumPlacements = 0;

    @MinNumber(0)
    @MaxNumber(512)
    @Desc("The maximum number of placements allowed for this piece. Zero means unbounded within the structure safety cap.")
    private int maximumPlacements = 0;

    @Desc("Whether this piece is a physical terminal cap. A terminal piece may consume a matching connector but must not continue expansion.")
    private boolean terminal = false;

    public boolean allowsDepth(int depth) {
        return depth >= minimumDepth && depth <= maximumDepth;
    }

    public boolean allowsPlacement(int existingPlacements) {
        return existingPlacements >= 0
                && (maximumPlacements == 0 || existingPlacements < maximumPlacements);
    }

    public boolean requiresMorePlacements(int existingPlacements) {
        return existingPlacements >= 0 && existingPlacements < minimumPlacements;
    }
}
