package art.arcane.iris.engine.modifier;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedModifier;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;

public class IrisCustomModifier extends EngineAssignedModifier<PlatformBlockState> {
    public IrisCustomModifier(Engine engine) {
        super(engine, "Custom");
    }

    @Override
    public void onModify(int x, int z, Hunk<PlatformBlockState> output, boolean multicore, ChunkContext context) {
        MantleChunk<Matter> mc = getEngine().getMantle().getMantle().getChunk(x >> 4, z >> 4);
        if (!mc.isFlagged(MantleFlag.CUSTOM_ACTIVE)) {
            return;
        }
        mc.use();

        BurstExecutor burst = MultiBurst.burst.burst(output.getHeight());
        burst.setMulticore(multicore);
        // complete() must run before release() even when queueing throws — detached burst
        // tasks must never write into a released chunk, and a lost permit wedges close().
        try {
            for (int y = 0; y < output.getHeight(); y++) {
                int finalY = y;
                burst.queue(() -> {
                    for (int rX = 0; rX < output.getWidth(); rX++) {
                        for (int rZ = 0; rZ < output.getDepth(); rZ++) {
                            PlatformBlockState b = output.get(rX, finalY, rZ);
                            if (b == null || !b.isCustom()) {
                                continue;
                            }
                            String placementKey = b.deferredPlacementKey();
                            PlatformBlockState baseState = b.placementBaseState();
                            if (placementKey == null || baseState == null) {
                                continue;
                            }

                            mc.getOrCreate(finalY >> 4)
                                    .slice(Identifier.class)
                                    .set(rX, finalY & 15, rZ, Identifier.fromString(placementKey));
                            output.set(rX, finalY, rZ, baseState);
                        }
                    }
                });
            }
        } finally {
            try {
                burst.complete();
            } finally {
                mc.release();
            }
        }
    }
}
