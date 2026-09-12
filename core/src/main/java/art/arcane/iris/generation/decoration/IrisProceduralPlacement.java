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

package art.arcane.iris.generation.decoration;

import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

/**
 * A procedurally generated, placeable thing in a biome or region (trees, ruins,
 * formations, coral, fungi, crystals). Implementations bake a deterministic pool
 * of in-memory IrisObject variants and expose the placement controls the engine
 * needs to scatter them, exactly like an object placement but generated from
 * scratch instead of loaded from an iob file.
 */
public interface IrisProceduralPlacement {
    String getName();

    double getChance();

    int getDensity();

    boolean isPlausible();

    IrisObjectPlacement asPlacement();

    IrisObject getVariantObject(IrisData data, RNG rng);

    KList<IrisObject> getVariantObjects(IrisData data);
}
