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
import art.arcane.iris.engine.mantle.MantleComponent;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import com.volmit.host.LoadedPack;
import com.volmit.host.MantleRunner;
import com.volmit.host.ResolvedGraph;
import com.volmit.host.ResolvedStage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Runs one mantle stage of the pack, Iris's built-in ones included, as a mantle component of its own: a pass of
 * Iris's mantle at the stage's place in the resolved order, so the stage sees everything earlier stages wrote
 * into the chunks around it. Its radius is the stage's reach, worked out from the pack when the components are
 * built; a dimension's {@code disabledComponents} turns it off, by its flag when it is one of Iris's own, or by its id.
 */
public final class ModuleMantleComponent implements MantleComponent {
    private final Engine engine;
    private final LoadedPack pack;
    private final MantleRunner runner;
    private final ResolvedStage stage;
    private final MantleFlag flag;
    private final int priority;
    private final int reachBlocks;
    private final boolean enabled;

    private ModuleMantleComponent(Engine engine, LoadedPack pack, MantleRunner runner, ResolvedStage stage,
                                  MantleFlag flag, int priority, int reachBlocks, boolean enabled) {
        this.engine = engine;
        this.pack = pack;
        this.runner = runner;
        this.stage = stage;
        this.flag = flag;
        this.priority = priority;
        this.reachBlocks = reachBlocks;
        this.enabled = enabled;
    }

    /**
     * One component per mantle stage of the engine's pack, in resolved order, each its own pass. A stage named in
     * {@code disabledComponents} is turned off; a name that matches no stage fails.
     */
    public static List<ModuleMantleComponent> forPack(Engine engine, Collection<String> disabledComponents) {
        return forPack(engine, engine.getData().getModules().pack(), disabledComponents);
    }

    static List<ModuleMantleComponent> forPack(Engine engine, LoadedPack pack, Collection<String> disabledComponents) {
        ResolvedGraph graph = pack.mantle();
        DisabledComponents disabled = new DisabledComponents(disabledComponents);
        disabled.requireKnown(graph.stages());
        MantleRunner runner = new MantleRunner(graph);
        IrisPackSettings settings = new IrisPackSettings(engine);
        List<MantleFlag> flags = MantleStageFlags.assign(graph.stages());
        List<ModuleMantleComponent> components = new ArrayList<>(flags.size());
        for (int position = 0; position < flags.size(); position++) {
            ResolvedStage stage = graph.stages().get(position);
            MantleFlag flag = flags.get(position);
            components.add(new ModuleMantleComponent(engine, pack, runner, stage, flag, position,
                    stage.reachBlocks(settings), !disabled.disables(stage)));
        }
        return List.copyOf(components);
    }

    /** The stage this component runs. */
    public ResolvedStage stage() {
        return stage;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public int getRadius() {
        return reachBlocks;
    }

    @Override
    public MantleFlag getFlag() {
        return flag;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        generate(x, z, writer::acquireChunk, new IrisMantleChunk(engine, writer, x, z, context));
    }

    /**
     * Runs the stage for chunk {@code x}, {@code z}, with the mantle chunks within the stage's reach of it and
     * {@code irisChunk} as Iris's own view of the chunk.
     */
    void generate(int x, int z, IrisModuleMantle.Chunks chunks, IrisMantleChunk irisChunk) {
        IrisMantleGenerationContext inputs = new IrisMantleGenerationContext(engine, x, z, reachBlocks, chunks, pack.catalog().mantle(),
                irisChunk);
        runner.generate(stage.stage(), inputs, ModuleSeeds.chunk(engine.getSeedManager().getSeed(), x, z),
                new StageMetrics(engine.getMetrics()));
    }
}
