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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.floating.FloatingChildSettings;
import art.arcane.iris.builtin.floating.MergeFloatingChildrenSettings;
import com.volmit.FeatureRegistry;
import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.core.modules.IrisChunk;
import art.arcane.iris.engine.decorator.DecorationScene;
import art.arcane.iris.core.modules.IrisMantleChunk;
import art.arcane.iris.core.modules.IrisPackSettings;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.components.FloatingObjectPlacing;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLog;
import com.volmit.config.PackSettings;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.MantleStageContext;
import com.volmit.stage.StageGraph;

/**
 * Module {@code iris:floating}: the islands of floating child biomes, their objects in the mantle after the
 * dimension's other objects, their solids after smoothing and their decoration after the chunk's own. The
 * mantle stage is a port of what was Iris's floating object mantle component, its work done by
 * {@link FloatingObjectPlacing}, whose reach is the pack's largest floating object placement. The chunk
 * stages' work is done by {@link FloatingIslands}.
 */
public final class FloatingModule implements IrisModule {
    private static final String VERSION = "built-in";

    private ModuleLog log;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:floating", "Floating islands", VERSION);
    }

    @Override
    public void register(FeatureRegistry registry) {
        log = registry.log();
        registry.registerSettings(FloatingChildSettings.class);
        registry.registerSettings(MergeFloatingChildrenSettings.class);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(IrisStages.Floating.OBJECTS).softAfter(IrisStages.Objects.PLACE.ref()).reach(this::objectReach)
                .run(FloatingModule::objects);
        // Ordering only: iris:post ships in the built-ins jar, which a pack may not have.
        graph.define(IrisStages.Floating.SOLIDS).softAfter(IrisStages.Post.SMOOTH.ref()).run(FloatingModule::solids);
        // Ordering only: iris:decorant ships in the built-ins jar, which a pack may not have.
        graph.define(IrisStages.Floating.DECORATE).softAfter(IrisStages.Decorant.DECORATE.ref()).run(FloatingModule::decorate);
    }

    private static void objects(MantleStageContext context) {
        IrisMantleChunk chunk = IrisMantleChunk.current();
        objectPlacing(chunk.engine(), context.log()).place(chunk.writer(), chunk.chunkX(), chunk.chunkZ(), chunk.context());
    }

    private int objectReach(PackSettings settings) {
        return objectPlacing(IrisPackSettings.of(settings).engine(), log).reach();
    }

    private static void solids(ChunkStageContext context) {
        IrisChunk chunk = IrisChunk.current();
        islands(chunk, context.log()).fillSolids(chunk.originX(), chunk.originZ(), chunk.blocks(), chunk.multicore(), chunk.context());
    }

    private static void decorate(ChunkStageContext context) {
        IrisChunk chunk = IrisChunk.current();
        islands(chunk, context.log()).decorateColumns(chunk.originX(), chunk.originZ(), chunk.blocks(), chunk.multicore(), chunk.context(),
                DecorationScene.of(context));
    }

    private static FloatingObjectPlacing objectPlacing(Engine engine, ModuleLog log) {
        return engine.getGeneration().state(FloatingObjectPlacing.class, generating -> new FloatingObjectPlacing(generating, log));
    }

    private static FloatingIslands islands(IrisChunk chunk, ModuleLog log) {
        return chunk.state(FloatingIslands.class, generating -> new FloatingIslands(generating, log));
    }
}
