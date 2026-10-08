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
import art.arcane.iris.core.modules.IrisMantleChunk;
import art.arcane.iris.core.modules.IrisPackSettings;
import art.arcane.iris.engine.framework.Engine;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.config.PackSettings;
import com.volmit.stage.MantleStageContext;
import com.volmit.stage.StageGraph;

/**
 * Module {@code iris:objects}: the objects the dimension's regions and biomes place, written into the mantle
 * once the caves they may anchor in are planned. A port of what was Iris's object mantle component, its work
 * done by {@link ObjectPlacing}, one per engine runtime. Its reach is the pack's largest object placement.
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

    private static void place(MantleStageContext context) {
        IrisMantleChunk chunk = IrisMantleChunk.current();
        placing(chunk.engine()).place(chunk.writer(), chunk.chunkX(), chunk.chunkZ(), chunk.context());
    }

    private static int reach(PackSettings settings) {
        return placing(IrisPackSettings.of(settings).engine()).reach();
    }

    /** The engine runtime's object placing, which {@link Engine#getObjectsAt} also asks. */
    public static ObjectPlacing placing(Engine engine) {
        return engine.getGeneration().state(ObjectPlacing.class, ObjectPlacing::new);
    }
}
