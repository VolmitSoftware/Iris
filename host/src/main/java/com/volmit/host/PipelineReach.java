package com.volmit.host;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** The most any stage of a pipeline writes beyond its footprint, so the host can prepare the area and
 * mantle a chunk or placement may touch. Inactive stages count too: activation is decided per
 * invocation, the area once per pack.
 *
 * @param blocks the largest {@link StageReach#blocks()} of the pipeline's stages, in blocks along X and Z
 * @param mantleLayers for each mantle layer some stage writes, by layer id in id order, the largest
 *     reach of the stages that write it
 */
public record PipelineReach(int blocks, Map<String, Integer> mantleLayers) {
    public PipelineReach {
        if (blocks < 0) throw new IllegalArgumentException("Negative reach: " + blocks);
        mantleLayers = Collections.unmodifiableMap(new TreeMap<>(mantleLayers));
    }

    static PipelineReach of(Iterable<ResolvedStage> stages) {
        int blocks = 0;
        var layers = new TreeMap<String, Integer>();
        for (var stage : stages) {
            int stageBlocks = stage.fixedReachBlocks();
            blocks = Math.max(blocks, stageBlocks);
            for (var layer : stage.reach().mantleLayers()) {
                layers.merge(layer.id(), stageBlocks, Math::max);
            }
        }
        return new PipelineReach(blocks, layers);
    }
}
