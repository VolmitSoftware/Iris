package com.volmit.example.chunk;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.block.BlockState;
import com.volmit.config.PackSettings;
import com.volmit.generation.ChunkContext;
import com.volmit.geometry.BlockBox;
import com.volmit.data.StorageCodec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import com.volmit.resource.ResourceId;
import com.volmit.stage.ChunkStage;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.MantleStage;
import com.volmit.stage.MantleStageContext;
import com.volmit.stage.Stage;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Work across chunks: a mantle stage plans veins into the mantle, past its own chunk, and a chunk stage
 * later turns its own chunk's part of the mantle into blocks. The two pipelines meet only in the mantle.
 * How far a vein runs past its chunk depends on the longest vein the pack configures, so the stage works
 * its reach out when the pack loads.
 */
public final class VeinModule implements IrisModule {
    public static final MantleKey<Integer> ORE = new MantleKey<>(ResourceId.parse("example:veins/ore"),Integer.class);
    public static final MantleStage PLAN = Stage.mantle("example:veins/plan");
    public static final ChunkStage<Void> PLACE = Stage.chunk("example:veins/place");
    public static final BlockState ORE_BLOCK = new BlockState("minecraft:iron_ore");
    private static final int PLANNED = 1;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("example:veins", "Veins", "1.0.0");
    }

    @Override
    public void setup(ModuleSetup setup) {
        setup.registry().registerMantle(ORE, MantleLifetime.GENERATION, new IntegerCodec());
        setup.stages().define(PLAN).when(VeinSettings.class).reach(VeinModule::reach).writesMantle(ORE).run(VeinModule::plan);
        setup.stages().define(PLACE).run(VeinModule::place);
    }

    /** How far the longest vein any level of the pack declares runs past its chunk. */
    public static int reach(PackSettings settings) {
        int longest = settings.declared(VeinSettings.class).values().stream().mapToInt(VeinSettings::length).max().orElse(0);
        return Math.max(0, longest - (ChunkContext.CHUNK_WIDTH - ChunkContext.CENTRE_COLUMN));
    }

    /** One vein per level that configures veins at the chunk's centre column: levels stack. */
    private static void plan(MantleStageContext context) {
        int startX = context.centreX();
        int z = context.centreZ();
        var ore = context.mantle().layer(ORE);
        for (var vein : context.settings().at(VeinSettings.class, startX, z).values()) {
            for (int step = 0; step < vein.length(); step++) {
                ore.set(startX + step, vein.y(), z, PLANNED);
            }
        }
    }

    private static void place(ChunkStageContext context) {
        var blocks = context.blocks();
        var chunk = new BlockBox(context.originX(), 0, context.originZ(),
                context.originX() + blocks.width(), blocks.height(), context.originZ() + blocks.depth());
        context.mantle().layer(ORE).visit(chunk, (x, y, z, planned) ->
                blocks.set(x - context.originX(), y, z - context.originZ(), ORE_BLOCK));
    }

    private static final class IntegerCodec implements StorageCodec<Integer> {
        public int version() { return 0; }
        public void write(DataOutput output, Integer value) throws IOException { output.writeInt(value); }
        public Integer read(DataInput input, int storedVersion) throws IOException { return input.readInt(); }
    }
}
