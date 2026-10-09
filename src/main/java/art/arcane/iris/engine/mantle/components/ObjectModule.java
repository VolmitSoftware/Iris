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
import com.volmit.config.PackSettings;
import com.volmit.service.ObjectsAt;
import com.volmit.stage.MantleStageContext;
import com.volmit.stage.StageGraph;
import com.volmit.world.PackWorld;

import java.util.Set;

/**
 * Module {@code iris:objects}: the objects the dimension's regions and biomes place, written into the mantle
 * once the caves they may anchor in are planned. A port of what was Iris's object mantle component, its work
 * done by {@link ObjectPlacing}, one per engine runtime. Its reach is the pack's largest object placement. It tells
 * Iris which objects a chunk would get ({@link ObjectsAt}), for the object search and studio tools.
 */
public final class ObjectModule implements IrisModule {
    private static final String VERSION = "built-in";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:objects", "Objects", VERSION);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(IrisStages.Objects.PLACE).requires(IrisStages.Carve.CAVES).reach(ObjectModule::reach).run(ObjectModule::place);
    }

    @Override
    public void register(FeatureRegistry registry) {
        registry.registerService(ObjectsAt.class, ObjectModule::objectsAt);
        registry.registerHeldType(TreeCanopy.class);
        registry.registerHeldType(TreeDecorator.class);
        registry.registerHeldType(TreeSecondaryLeaf.class);
        registry.registerHeldType(Coral.class);
        registry.registerHeldType(Crystal.class);
        registry.registerHeldType(Formation.class);
        registry.registerHeldType(Fungus.class);
        registry.registerHeldType(Ruin.class);
    }

    private static void place(MantleStageContext context) {
        IrisMantleChunk chunk = IrisMantleChunk.current();
        placing(chunk.engine()).place(chunk.writer(), chunk.chunkX(), chunk.chunkZ(), chunk.context());
    }

    private static int reach(PackSettings settings) {
        return placing(IrisPackSettings.of(settings).engine()).reach();
    }

    /** The keys of the objects the surface placements of the chunk's biome and region would place, guessed without generating it. */
    private static Set<String> objectsAt(PackWorld world, int chunkX, int chunkZ) {
        return placing(IrisPackWorld.of(world).engine()).guess(chunkX, chunkZ);
    }

    /** The engine runtime's object placing, which the module's {@link ObjectsAt} also asks. */
    public static ObjectPlacing placing(Engine engine) {
        return engine.getGeneration().state(ObjectPlacing.class, ObjectPlacing::new);
    }
}
