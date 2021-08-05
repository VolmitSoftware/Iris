/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.framework;

import com.volmit.iris.core.gui.components.Renderer;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.objects.IrisObjectPlacement;
import com.volmit.iris.engine.object.regional.IrisRegion;
import com.volmit.iris.engine.parallax.ParallaxAccess;
import com.volmit.iris.util.data.DataProvider;

public interface GeneratorAccess extends DataProvider, Renderer {
    IrisRegion getRegion(int x, int z);

    ParallaxAccess getParallaxAccess();

    IrisData getData();

    IrisBiome getCaveBiome(int x, int z);

    IrisBiome getSurfaceBiome(int x, int z);

    int getHeight(int x, int z);

    default IrisBiome getBiome(int x, int y, int z) {
        if (y <= getHeight(x, z) - 2) {
            return getCaveBiome(x, z);
        }

        return getSurfaceBiome(x, z);
    }

    default PlacedObject getObjectPlacement(int x, int y, int z) {
        String objectAt = getParallaxAccess().getObject(x, y, z);

        if (objectAt == null || objectAt.isEmpty()) {
            return null;
        }

        String[] v = objectAt.split("\\Q@\\E");
        String object = v[0];
        int id = Integer.parseInt(v[1]);
        IrisRegion region = getRegion(x, z);

        for (IrisObjectPlacement i : region.getObjects()) {
            if (i.getPlace().contains(object)) {
                return new PlacedObject(i, getData().getObjectLoader().load(object), id, x, z);
            }
        }

        IrisBiome biome = getBiome(x, y, z);

        for (IrisObjectPlacement i : biome.getObjects()) {
            if (i.getPlace().contains(object)) {
                return new PlacedObject(i, getData().getObjectLoader().load(object), id, x, z);
            }
        }

        return new PlacedObject(null, getData().getObjectLoader().load(object), id, x, z);
    }

    int getCacheID();
}
