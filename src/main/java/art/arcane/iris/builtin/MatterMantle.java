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

import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;

/**
 * Iris's mantle as the matter slices it stores, for the built-ins whose data the platform and the rest of Iris read as
 * slices rather than through a {@link com.volmit.mantle.MantleLayer}: caves ({@code MatterCavern}) and markers. The
 * slices are VolmLib's own types, which a pack module may use as well. Iris offers it as an extension of its mantle and
 * chunk invocations: {@code context.extension(MatterMantle.class)}.
 *
 * <p>A chunk invocation's matter mantle holds its own chunk only, which the stage may write, as a chunk stage writes the
 * mantle of its own chunk; it reads its neighbours through {@link #loadedChunk} alone.
 *
 * <p>Heights are as the mantle stores them: above the dimension's bottom. A chunk the invocation reaches is pinned for
 * as long as the invocation runs; callers neither release it nor keep it afterwards.
 */
public interface MatterMantle {
    /** The mantle chunk at chunk coordinates {@code chunkX}, {@code chunkZ}, or null outside what the invocation reaches. */
    MantleChunk<Matter> chunk(int chunkX, int chunkZ);

    /**
     * The mantle chunk at those coordinates if the world holds it in memory, wherever it is, or null. It is never loaded
     * or created for the call and not pinned, so read it at once and do not keep it.
     */
    MantleChunk<Matter> loadedChunk(int chunkX, int chunkZ);
}
