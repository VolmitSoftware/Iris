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

package art.arcane.iris.builtin;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.volmlib.util.math.RNG;

/**
 * The random streams and noise of Iris's own generators that decide where existing worlds have their objects, which no
 * API noise reproduces: Iris's chunk random, and the noise of a placement's density style and virtual heightmap. Iris
 * offers it as an extension of its worlds: {@code world.extension(LegacyStreams.class)}.
 */
public interface LegacyStreams {
    /** The random a chunk's objects are placed with: the same for the same chunk of the same world. */
    RNG objectChunkRandom(int chunkX, int chunkZ);

    /** How many objects the placement places at the column: its fixed density, or what its density style gives there for {@code rng}. */
    int density(ObjectPlacement placement, RNG rng, double x, double z);

    /** The height the placement's virtual heightmap gives the column, its noise seeded with {@code seed}; for a placement with a heightmap. */
    int heightmapSurface(ObjectPlacement placement, long seed, int x, int z);
}
