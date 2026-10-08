package com.volmit.host;

import com.volmit.mantle.MantleKey;
import com.volmit.stage.PackReach;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** What one stage declares it writes outside its footprint.
 *
 * @param blocks how far, in blocks along X and Z, the stage writes blocks or mantle values beyond its
 *     footprint: the chunk's 16 by 16 columns for a mantle stage, the X/Z bounds of the placed object
 *     for a placement stage. 0 means only inside it, and is all a chunk stage may declare. Height is not
 *     limited: the footprint is whole columns. A number declared at install, or for a mantle stage a
 *     {@link PackReach} worked out from the pack's settings when the pack loads.
 * @param mantleLayers the mantle layers the stage writes, each once, in declaration order
 */
public record StageReach(PackReach blocks, List<MantleKey<?>> mantleLayers) {
    /** A stage that declared nothing: it writes blocks inside its footprint only, and no mantle layer. */
    public static final StageReach NONE = new StageReach(FixedReach.NONE, List.of());

    public StageReach {
        Objects.requireNonNull(blocks, "blocks");
        mantleLayers = List.copyOf(mantleLayers);
        var ids = new HashSet<String>();
        for (var layer : mantleLayers) {
            if (!ids.add(Objects.requireNonNull(layer, "mantle layer").id())) {
                throw new IllegalArgumentException("Mantle layer " + layer.id() + " declared twice");
            }
        }
    }

    /** A reach of a number of blocks declared at install. */
    public StageReach(int blocks, List<MantleKey<?>> mantleLayers) {
        this(new FixedReach(blocks), mantleLayers);
    }
}
