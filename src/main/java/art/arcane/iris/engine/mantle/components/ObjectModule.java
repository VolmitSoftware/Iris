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
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.modules.objects.ProceduralObjectSettings;
import art.arcane.iris.builtin.objects.RequireSurfaceSupportSettings;
import art.arcane.iris.builtin.objects.SurfaceSupportBufferSettings;
import art.arcane.iris.modules.objects.TreeGrowthSettings;
import art.arcane.iris.builtin.objects.UpperObjectsForceSettings;
import art.arcane.iris.builtin.objects.UpperObjectsSettings;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLifecycle;
import com.volmit.ModuleLog;
import com.volmit.ModuleSetup;
import com.volmit.config.PackSettings;
import com.volmit.service.ObjectsAt;
import com.volmit.service.SaplingGrowth;
import com.volmit.stage.MantleStageContext;
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

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:objects", "Objects", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        ModuleLog log = setup.log();
        // Ordering only: iris:carve ships in the built-ins jar, which a pack may not have.
        setup.stages().define(IrisStages.Objects.PLACE).softAfter(IrisStages.Carve.CAVES).reach(settings -> reach(settings, log))
                .run(ObjectModule::place);
        var registry = setup.registry();
        registry.registerService(ObjectsAt.class, new ObjectLocating());
        registry.registerService(SaplingGrowth.class, new TreeGrowing());
        registry.registerSettings(ObjectSettings.class, ProceduralObjectSettings.class, TreeGrowthSettings.class,
                RequireSurfaceSupportSettings.class, SurfaceSupportBufferSettings.class, UpperObjectsSettings.class,
                UpperObjectsForceSettings.class);
        registry.registerPlacementType(ObjectPlacement.class, ObjectPlacement.jsonFields());
        registry.registerLifecycle(this);
    }

    @Override
    public void warmUp(PackWorld world) {
        new ObjectWarmUp(world).warm();
    }

    private static void place(MantleStageContext context) {
        ObjectPlacing.of(context.world()).place(context);
    }

    private static int reach(PackSettings settings, ModuleLog log) {
        return new PlacementReach(settings.resources(), log).ofPack(settings);
    }
}
