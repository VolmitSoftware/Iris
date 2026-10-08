/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.modules;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.IrisMantleComponent;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import com.volmit.host.LoadedPack;
import com.volmit.host.MantleRunner;
import com.volmit.host.ResolvedGraph;

/**
 * Runs the pack's mantle pipeline, Iris's built-in mantle stages included, as one of Iris's mantle components:
 * once for every chunk the mantle prepares around a generating chunk, as far as the pipeline's declared reach.
 * It is the first pass, where carving has always run, so Iris's object, floating object and structure passes
 * see the caves and whatever else mantle stages wrote. It is disabled while the pack has no mantle stages.
 */
public final class ModuleMantleComponent extends IrisMantleComponent {
    /** Marks a mantle chunk whose module stages have run. */
    public static final MantleFlag FLAG = MantleFlag.of(MantleFlag.MIN_ORDINAL);
    /** The first pass: the carving stage it runs had priority 0 as a component of its own. */
    private static final int PRIORITY = 0;

    /** Whether the pack has mantle stages; asked on every chunk, so kept until the next hotload. */
    private volatile Boolean hasStages;

    public ModuleMantleComponent(EngineMantle engineMantle) {
        super(engineMantle, FLAG, PRIORITY);
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && hasStages();
    }

    @Override
    public void hotload() {
        super.hotload();
        hasStages = null;
    }

    @Override
    protected int computeRadius() {
        return pipeline().reach().blocks();
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        Engine engine = getEngineMantle().getEngine();
        IrisMantleChunk chunk = new IrisMantleChunk(engine, writer, x, z, context);
        chunk.runBound(() -> generate(engine.getData().getModules().pack(), engine, x, z, getRadius(), writer::acquireChunk));
    }

    /** Runs the mantle stages of chunk {@code x}, {@code z}, with the mantle chunks within {@code reachBlocks} of it. */
    static void generate(LoadedPack pack, Engine engine, int x, int z, int reachBlocks, IrisModuleMantle.Chunks chunks) {
        IrisMantleGenerationContext inputs = new IrisMantleGenerationContext(engine, x, z, reachBlocks, chunks, pack.catalog().mantle());
        long seed = ModuleSeeds.chunk(engine.getSeedManager().getSeed(), x, z);
        new MantleRunner(pack.mantle()).generate(inputs, seed);
    }

    private boolean hasStages() {
        Boolean known = hasStages;
        if (known == null) {
            known = !pipeline().stages().isEmpty();
            hasStages = known;
        }
        return known;
    }

    private ResolvedGraph pipeline() {
        return getEngineMantle().getEngine().getData().getModules().pack().mantle();
    }
}
