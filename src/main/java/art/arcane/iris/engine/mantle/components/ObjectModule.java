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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.builtin.objects.coral.Coral;
import art.arcane.iris.builtin.objects.crystal.Crystal;
import art.arcane.iris.builtin.objects.formation.Formation;
import art.arcane.iris.builtin.objects.fungi.Fungus;
import art.arcane.iris.builtin.objects.ruin.Ruin;
import art.arcane.iris.builtin.objects.tree.TreeCanopy;
import art.arcane.iris.builtin.objects.tree.TreeDecorator;
import art.arcane.iris.builtin.objects.tree.TreeSecondaryLeaf;
import art.arcane.iris.core.modules.IrisMantleChunk;
import art.arcane.iris.core.modules.IrisPackSettings;
import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.framework.Engine;
import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLifecycle;
import com.volmit.ModuleLog;
import com.volmit.config.PackSettings;
import com.volmit.service.ObjectsAt;
import com.volmit.service.SaplingGrowth;
import com.volmit.stage.MantleStageContext;
import com.volmit.stage.StageGraph;
import com.volmit.world.PackWorld;

/**
 * Module {@code iris:objects}: the objects the dimension's regions and biomes place, written into the mantle
 * once the caves they may anchor in are planned. A port of what was Iris's object mantle component, its work
 * done by {@link ObjectPlacing}, one per engine runtime, with the noise of its placements and the variants of its procedural
 * objects built ahead of generation ({@link #warmUp}). Its reach is the pack's largest object placement. It tells
 * Iris which objects a chunk would get and which biomes and regions place an object ({@link ObjectLocating}), for the
 * object search and studio tools, and grows the trees of saplings ({@link TreeGrowing}).
 */
public final class ObjectModule implements IrisModule, ModuleLifecycle {
    private static final String VERSION = "built-in";

    private ModuleLog log;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:objects", "Objects", VERSION);
    }

    @Override
    public void install(StageGraph graph) {
        // Ordering only: iris:carve ships in the built-ins jar, which a pack may not have.
        graph.define(IrisStages.Objects.PLACE).softAfter(IrisStages.Carve.CAVES.ref()).reach(this::reach).run(ObjectModule::place);
    }

    @Override
    public void register(FeatureRegistry registry) {
        log = registry.log();
        registry.registerService(ObjectsAt.class, new ObjectLocating());
        registry.registerService(SaplingGrowth.class, new TreeGrowing());
        registry.registerHeldType(TreeCanopy.class);
        registry.registerHeldType(TreeDecorator.class);
        registry.registerHeldType(TreeSecondaryLeaf.class);
        registry.registerHeldType(Coral.class);
        registry.registerHeldType(Crystal.class);
        registry.registerHeldType(Formation.class);
        registry.registerHeldType(Fungus.class);
        registry.registerHeldType(Ruin.class);
        registry.registerLifecycle(this);
    }

    @Override
    public void warmUp(PackWorld world) {
        new ObjectWarmUp(IrisPackWorld.of(world).engine()).warm();
    }

    private static void place(MantleStageContext context) {
        IrisMantleChunk chunk = IrisMantleChunk.current();
        placing(chunk.engine(), context.log()).place(chunk.writer(), chunk.chunkX(), chunk.chunkZ(), chunk.context());
    }

    private int reach(PackSettings settings) {
        return placing(IrisPackSettings.of(settings).engine(), log).reach();
    }

    /** The engine runtime's object placing, which the module's {@link ObjectsAt} also asks; {@code log} is the module's. */
    public static ObjectPlacing placing(Engine engine, ModuleLog log) {
        return engine.getGeneration().state(ObjectPlacing.class, generating -> new ObjectPlacing(generating, log));
    }
}
