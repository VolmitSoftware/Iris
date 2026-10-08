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

import art.arcane.iris.builtin.anchor.AnchorStages;
import art.arcane.iris.builtin.bore.BoreStages;
import art.arcane.iris.builtin.prepare.PrepareStages;
import art.arcane.iris.builtin.validate.ValidateStages;
import art.arcane.iris.core.modules.IrisPlacement;
import art.arcane.iris.engine.object.prefab.IrisObject;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.StageGraph;

/** Module {@code iris:bore}: fills the object's volume with air, extended up and down as configured. */
public final class BoreModule implements IrisModule {
    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:bore", "Bore", PlacementModules.VERSION);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(BoreStages.CLEAR)
                .requires(AnchorStages.FINAL, ValidateStages.COLLISIONS)
                .run(BoreModule::clear);
    }

    private static void clear(PlacementStageContext context) {
        PreparedPlacement prepared = context.output(PrepareStages.TRANSFORMS);
        IrisObjectPlacement config = prepared.config();
        if (!config.isBore()) {
            return;
        }
        IrisPlacement placement = IrisPlacement.current();
        WorldVolume volume = WorldVolume.of(placement, prepared, context.output(AnchorStages.FINAL).y());
        PlacementWorld world = new PlacementWorld(context.access(), placement.states());
        for (int x = volume.minX(); x <= volume.maxX(); x++) {
            for (int y = volume.minY() - config.getBoreExtendMinY(); y <= volume.maxY() + config.getBoreExtendMaxY(); y++) {
                for (int z = volume.minZ(); z <= volume.maxZ(); z++) {
                    world.set(x, y, z, IrisObject.States.AIR);
                }
            }
        }
    }
}
