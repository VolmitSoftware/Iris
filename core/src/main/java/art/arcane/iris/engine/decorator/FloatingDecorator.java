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

package art.arcane.iris.engine.decorator;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDecorationPart;
import art.arcane.iris.engine.object.IrisDecorator;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.spi.PlatformBlockState;

/*
 * Floating island decoration path. Bypasses all canGoOn, slope, whitelist, and blacklist
 * gating from IrisSurfaceDecorator — the island top IS the biome's designated surface by
 * construction, so those material-compatibility checks are never meaningful here.
 */
public class FloatingDecorator {

    public static int decorateColumn(Engine engine, IrisBiome target, IrisDecorationPart part,
                                     int xf, int zf, int realX, int realZ,
                                     int height, int max, Hunk<PlatformBlockState> data, RNG rng,
                                     Runnable candidatesNullCallback) {
        RNG gRNG = new RNG(DecoratorCore.partSeed(engine.getSeedManager().getDecorator(), part));
        IrisDecorator decorator = DecoratorCore.pickDecorator(target, part, gRNG, rng, engine.getData(), realX, realZ);

        if (decorator == null) {
            candidatesNullCallback.run();
            return 0;
        }

        if (!decorator.isStacking()) {
            DecoratorCore.placeFloatingSimple(decorator, xf, zf, realX, realZ, height, max, data,
                    rng, engine.getData(), engine.getMantle());
            return max > 1 ? 1 : 0;
        }

        return DecoratorCore.placeFloatingStacked(decorator, xf, zf, realX, realZ, height, max, data,
                rng, engine.getData(), engine.getMantle());
    }
}
