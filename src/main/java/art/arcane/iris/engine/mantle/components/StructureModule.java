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
import art.arcane.iris.builtin.structure.StructureHost;
import art.arcane.iris.builtin.structure.StructurePlacementSettings;
import art.arcane.iris.builtin.structure.StructureSettings;
import art.arcane.iris.engine.framework.structure.StructureResources;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.config.PackSettings;
import com.volmit.stage.MantleStageContext;

/**
 * Module {@code iris:structure}: the jigsaw structures, pools and pieces of a pack (its resource kinds), the structure
 * placements a pack declares, and the mantle stage that places the structures starting in each chunk. Iris builds and
 * places the structures ({@link StructureHost}); their validation, the locator and the platform structure answers are
 * Iris's own module {@code iris:structure-model}.
 * <p>
 * It is not in the built-ins jar: Iris registers it with its own modules, because Iris's jigsaw studio, importers and
 * exporters read the resources it registers as the same classes.
 */
public final class StructureModule implements IrisModule {
    private static final String VERSION = "built-in";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:structure", "Structures", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        setup.stages().define(IrisStages.Structure.PLACE).softAfter(IrisStages.Objects.PLACE, IrisStages.Floating.OBJECTS)
                .reach(StructureModule::reach).run(StructureModule::place);
        var registry = setup.registry();
        StructureResources.kinds().forEach(registry::registerResourceKind);
        registry.registerSettings(StructureSettings.class);
        registry.registerSettings(StructurePlacementSettings.class);
    }

    private static void place(MantleStageContext context) {
        context.world().extension(StructureHost.class).orElseThrow().placeChunk(context);
    }

    private static int reach(PackSettings settings) {
        return settings.extension(StructureHost.class).orElseThrow().reach();
    }
}
