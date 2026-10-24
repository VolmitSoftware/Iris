package com.volmit.host;

import com.volmit.config.PackSettings;
import com.volmit.resource.ResourceId;
import com.volmit.stage.Stage;
import com.volmit.stage.StageRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Diagnostic view of one stage at its resolved position. incoming lists every edge into this
 * stage, including soft edges other stages declared; absentSoftTargets lists this stage's soft
 * references that matched no installed stage and therefore add no ordering. reach is what the stage
 * declared it writes beyond its footprint; {@link #reachBlocks(PackSettings)} works it out for a pack.
 */
public record ResolvedStage(
        Stage<?> stage,
        ResourceId module,
        Optional<FeatureDescriptor<?>> settings,
        List<StageEdge> incoming,
        List<StageRef> absentSoftTargets,
        StageReach reach) {
    public ResolvedStage {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(reach, "reach");
        incoming = List.copyOf(incoming);
        absentSoftTargets = List.copyOf(absentSoftTargets);
    }

    /** How far, in blocks along X and Z, the stage writes beyond its footprint in the pack whose settings
     * are {@code settings}: the declared number, or what the stage's {@link com.volmit.stage.PackReach}
     * works out. The host asks once per pack load, before generating.
     *
     * @throws IllegalStateException when the stage works out a negative reach
     */
    public int reachBlocks(PackSettings settings) {
        Objects.requireNonNull(settings, "settings");
        int blocks = reach.blocks().blocks(settings);
        if (blocks < 0) {
            throw new IllegalStateException("Stage " + stage + " of module " + module + " worked out a negative reach of "
                    + blocks + " blocks for this pack");
        }
        return blocks;
    }

    /** The declared number of blocks; for a pipeline whose reaches do not depend on the pack. */
    int fixedReachBlocks() {
        if (!(reach.blocks() instanceof FixedReach fixed)) {
            throw new IllegalStateException("Stage " + stage + " of module " + module + " works out its reach when the pack"
                    + " loads; ask each mantle stage with ResolvedStage.reachBlocks(settings)");
        }
        return fixed.blocks();
    }
}
