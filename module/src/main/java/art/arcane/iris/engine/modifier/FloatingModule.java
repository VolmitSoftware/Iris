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

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.modules.floating.FloatingChildSettings;
import art.arcane.iris.modules.floating.MergeFloatingChildrenSettings;
import art.arcane.iris.engine.mantle.components.FloatingObjectPlacing;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLog;
import com.volmit.ModuleSetup;
import com.volmit.config.PackSettings;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.MantleStageContext;

/**
 * Module {@code iris:floating}: the islands of floating child biomes, their objects in the mantle after the
 * dimension's other objects, their solids after smoothing and their decoration after the chunk's own. The
 * mantle stage's work is done by {@link FloatingObjectPlacing}, whose reach is the pack's largest floating object
 * placement. The chunk stages' work is done by {@link FloatingIslands}.
 */
public final class FloatingModule implements IrisModule {
    private static final String VERSION = "1.0.0";

    private ModuleLog log;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:floating", "Floating islands", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        log = setup.log();
        var registry = setup.registry();
        registry.registerSettings(FloatingChildSettings.class);
        registry.registerSettings(MergeFloatingChildrenSettings.class);

        var graph = setup.stages();
        graph.define(IrisStages.Floating.OBJECTS).softAfter(IrisStages.Objects.PLACE).reach(this::objectReach)
                .run(FloatingModule::objects);
        // Ordering only: iris:post ships as a module jar, which a pack may not have.
        graph.define(IrisStages.Floating.SOLIDS).softAfter(IrisStages.Post.SMOOTH).run(FloatingModule::solids);
        // Ordering only: iris:decorant ships as a module jar, which a pack may not have.
        graph.define(IrisStages.Floating.DECORATE).softAfter(IrisStages.Decorant.DECORATE).run(FloatingModule::decorate);
    }

    private static void objects(MantleStageContext context) {
        FloatingObjectPlacing.of(context.world(), context.log()).place(context);
    }

    private int objectReach(PackSettings settings) {
        return FloatingObjectPlacing.reach(settings, log);
    }

    private static void solids(ChunkStageContext context) {
        FloatingIslands.of(context.world(), context.log()).fillSolids(context);
    }

    private static void decorate(ChunkStageContext context) {
        FloatingIslands.of(context.world(), context.log()).decorateColumns(context);
    }
}
