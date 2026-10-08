package com.volmit.example.chunk;

import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.PackSettings;
import com.volmit.mantle.BlockBox;
import com.volmit.mantle.MantleCodec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import com.volmit.stage.ChunkStage;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.MantleStage;
import com.volmit.stage.MantleStageContext;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

/** Work across chunks: a mantle stage plans veins into the mantle, past its own chunk, and a chunk stage
 * later turns its own chunk's part of the mantle into blocks. The two pipelines meet only in the mantle.
 * How far a vein runs past its chunk depends on the longest vein the pack configures, so the stage works
 * its reach out when the pack loads.
 */
public final class VeinModule implements IrisModule {
    public static final MantleKey<Integer> ORE = new MantleKey<>("example:veins/ore", Integer.class);
    public static final MantleStage<Void> PLAN = Stage.mantle("example:veins/plan");
    public static final ChunkStage<Void> PLACE = Stage.chunk("example:veins/place");
    public static final BlockState ORE_BLOCK = new BlockState("minecraft:iron_ore");
    private static final int CHUNK_WIDTH = 16;
    private static final int CHUNK_CENTRE = 7;
    private static final int PLANNED = 1;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("example:veins", "Veins", "1.0.0");
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(PLAN).when(VeinSettings.class).reach(VeinModule::reach).writesMantle(ORE).run(VeinModule::plan);
        graph.define(PLACE).run(VeinModule::place);
    }

    @Override
    public void register(FeatureRegistry registry) {
        registry.registerMantle(ORE, MantleLifetime.GENERATION, new IntegerCodec());
    }

    /** How far the longest vein any level of the pack declares runs past its chunk. */
    public static int reach(PackSettings settings) {
        int longest = settings.declared(VeinSettings.class).stream().mapToInt(vein -> vein.value().length()).max().orElse(0);
        return Math.max(0, longest - (CHUNK_WIDTH - CHUNK_CENTRE));
    }

    /** One vein per level that configures veins at the chunk's centre column: levels stack. */
    private static void plan(MantleStageContext context, List<Entry<VeinSettings>> inChunk) {
        int startX = context.originX() + CHUNK_CENTRE;
        int z = context.originZ() + CHUNK_CENTRE;
        var ore = context.mantle().layer(ORE);
        for (var vein : context.settings().at(VeinSettings.class, startX, z)) {
            for (int step = 0; step < vein.value().length(); step++) {
                ore.set(startX + step, vein.value().y(), z, PLANNED);
            }
        }
    }

    private static void place(ChunkStageContext context) {
        var blocks = context.hunk(BlockState.class);
        var chunk = new BlockBox(context.originX(), 0, context.originZ(),
                context.originX() + blocks.getWidth(), blocks.getHeight(), context.originZ() + blocks.getDepth());
        context.mantle().layer(ORE).visit(chunk, (x, y, z, planned) ->
                blocks.set(x - context.originX(), y, z - context.originZ(), ORE_BLOCK));
    }

    private static final class IntegerCodec implements MantleCodec<Integer> {
        public int version() { return 0; }
        public void write(DataOutput output, Integer value) throws IOException { output.writeInt(value); }
        public Integer read(DataInput input, int storedVersion) throws IOException { return input.readInt(); }
    }
}
