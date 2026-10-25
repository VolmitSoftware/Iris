/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.structure.object;

import art.arcane.iris.generation.cave.CarveResult;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.RNG;

import java.util.function.BiConsumer;

/** Entry point for executing an engine's resolved placement plan. */
final class IrisObjectPlacementRunner {
    private final IrisObject self;
    private final IrisObjectPlacementAlignment alignment;

    IrisObjectPlacementRunner(IrisObject self, IrisObjectPlacementAlignment alignment) {
        this.self = self;
        this.alignment = alignment;
    }

    int place(int x, int yv, int z, IObjectPlacer placer, IrisObjectPlacement config, RNG rng,
              BiConsumer<BlockPosition, NativeBlockState> listener, CarveResult cave, IrisData data) {
        PlacementContext context = new PlacementContext(config, data, placer, cave, rng, self, alignment, x, z, yv, listener);
        return PlacementLifecycles.forPlacer(placer).get(config.getMode()).execute(context).heightOrRejected();
    }
}
