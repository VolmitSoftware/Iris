package com.volmit.vanilla;

import com.volmit.block.BlockSpec;
import com.volmit.block.PaletteSpec;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import java.util.List;
import java.util.Objects;

/** Foundation columns the host builds beneath a structure's pieces after placing them, as packs write it:
 * {@code {"maxDepth": 32, "palette": {"palette": [{"block": "stone_bricks"}]}, "spacing": 2}}. A value the host
 * creates with the no-argument constructor and reads the JSON into.
 */
@Desc("Defines foundation columns placed beneath a structure.")
public final class StructureStilts {
    private static final int DEFAULT_MAX_DEPTH = 64;
    private static final String DEFAULT_BLOCK = "minecraft:cobblestone";

    @MinNumber(1)
    @MaxNumber(4064)
    @Desc("Maximum number of blocks each foundation column may descend while searching for solid ground.")
    private int maxDepth = DEFAULT_MAX_DEPTH;

    @Desc("Block palette used for foundation columns.")
    private PaletteSpec palette = PaletteSpec.of(List.of(BlockSpec.of(DEFAULT_BLOCK)));

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("Horizontal spacing between support columns. One fills every eligible foundation column.")
    private int spacing = 1;

    @Desc("Whether solid partial blocks such as slabs, stairs, and walls may seed foundation columns.")
    private boolean supportNonOccluding = false;

    private StructureStilts() {
    }

    private StructureStilts(int maxDepth, PaletteSpec palette, int spacing, boolean supportNonOccluding) {
        this.maxDepth = maxDepth;
        this.palette = Objects.requireNonNull(palette, "palette");
        this.spacing = spacing;
        this.supportNonOccluding = supportNonOccluding;
    }

    /** Columns of {@code palette} under every eligible block, at most 64 deep. */
    public static StructureStilts of(PaletteSpec palette) {
        return new StructureStilts(DEFAULT_MAX_DEPTH, palette, 1, false);
    }

    public StructureStilts withMaxDepth(int maxDepth) {
        return new StructureStilts(maxDepth, palette(), spacing, supportNonOccluding);
    }

    public StructureStilts withSpacing(int spacing) {
        return new StructureStilts(maxDepth, palette(), spacing, supportNonOccluding);
    }

    /** These stilts, also seeded from partial blocks such as slabs, stairs and walls. */
    public StructureStilts supportingNonOccluding() {
        return new StructureStilts(maxDepth, palette(), spacing, true);
    }

    public int maxDepth() {
        return maxDepth;
    }

    /** The blocks of the columns; cobblestone where the pack wrote none. */
    public PaletteSpec palette() {
        return palette == null ? PaletteSpec.of(List.of(BlockSpec.of(DEFAULT_BLOCK))) : palette;
    }

    public int spacing() {
        return spacing;
    }

    public boolean supportNonOccluding() {
        return supportNonOccluding;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StructureStilts that && maxDepth == that.maxDepth && palette().equals(that.palette())
                && spacing == that.spacing && supportNonOccluding == that.supportNonOccluding;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxDepth, palette(), spacing, supportNonOccluding);
    }

    @Override
    public String toString() {
        return "StructureStilts[" + palette() + ", depth " + maxDepth + ", spacing " + spacing + "]";
    }
}
