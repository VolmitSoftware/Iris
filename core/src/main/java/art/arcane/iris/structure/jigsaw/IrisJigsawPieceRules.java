package art.arcane.iris.structure.jigsaw;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Deterministic eligibility, placement-count, and terminal-role rules for one jigsaw piece.")
@Data
public class IrisJigsawPieceRules {
    @MinNumber(0)
    @MaxNumber(30)
    @Description("The shallowest assembly depth at which this piece may be placed. The start piece is depth zero.")
    private int minimumDepth = 0;

    @MinNumber(0)
    @MaxNumber(30)
    @Description("The deepest assembly depth at which this piece may be placed.")
    private int maximumDepth = 30;

    @MinNumber(0)
    @MaxNumber(512)
    @Description("The minimum number of placements required for this piece in a completed assembly.")
    private int minimumPlacements = 0;

    @MinNumber(0)
    @MaxNumber(512)
    @Description("The maximum number of placements allowed for this piece. Zero means unbounded within the structure safety cap.")
    private int maximumPlacements = 0;

    @Description("Whether this piece is a physical terminal cap. A terminal piece may consume a matching connector but must not continue expansion.")
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
