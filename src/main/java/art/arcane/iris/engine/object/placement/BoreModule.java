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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.modules.placement.PlacementSettings;
import art.arcane.iris.modules.placement.PlacementStages;
import art.arcane.iris.modules.placement.PreparedPlacement;
import art.arcane.iris.modules.placement.RunningPlacement;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.block.BlockState;
import com.volmit.stage.PlacementStageContext;

/** Module {@code iris:bore}: fills the object's volume with air, extended up and down as configured. */
public final class BoreModule implements IrisModule {
    private static final BlockState AIR = new BlockState("minecraft:cave_air");

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:bore", "Bore", PlacementModules.VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        setup.stages().define(PlacementStages.Bore.CLEAR)
                .requires(PlacementStages.Anchor.FINAL, PlacementStages.Validate.COLLISIONS)
                .run(BoreModule::clear);
    }

    private static void clear(PlacementStageContext context) {
        PreparedPlacement prepared = context.output(PlacementStages.Prepare.TRANSFORMS);
        PlacementSettings settings = prepared.settings();
        if (!settings.bore()) {
            return;
        }
        RunningPlacement placement = prepared.placement();
        WorldVolume volume = WorldVolume.of(placement, prepared, context.output(PlacementStages.Anchor.FINAL).y());
        PlacementWorld world = new PlacementWorld(context.access(), prepared.traits());
        for (int x = volume.minX(); x <= volume.maxX(); x++) {
            for (int y = volume.minY() - settings.boreExtendMinY(); y <= volume.maxY() + settings.boreExtendMaxY(); y++) {
                for (int z = volume.minZ(); z <= volume.maxZ(); z++) {
                    world.set(x, y, z, AIR);
                }
            }
        }
    }
}
