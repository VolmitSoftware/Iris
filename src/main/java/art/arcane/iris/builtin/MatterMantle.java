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
import com.volmit.host.ReadOnlyMantle;
import com.volmit.mantle.Mantle;

/**
 * Iris's mantle as the matter slices it stores, for the built-ins whose data the platform and the rest of Iris read as
 * slices rather than through a {@link com.volmit.mantle.MantleLayer}: caves ({@code MatterCavern}) and markers. The
 * slices are VolmLib's own types, which a pack module may use as well; Iris's worlds implement this next to
 * {@link Mantle}, and {@link #of(Mantle)} reaches it from an invocation's mantle.
 *
 * <p>Heights are as the mantle stores them: above the dimension's bottom. A chunk the invocation reaches is pinned for
 * as long as the invocation runs; callers neither release it nor keep it afterwards.
 */
public interface MatterMantle {
    /**
     * The matter side of {@code mantle}, which must be a mantle Iris hands to its stages. A chunk stage's mantle is read-only
     * to the stage; the matter behind it is not, and a built-in chunk stage that writes markers into it (the caves' floors and
     * ceilings) is why this reaches through.
     */
    static MatterMantle of(Mantle mantle) {
        if (mantle instanceof ReadOnlyMantle readOnly) {
            return of(readOnly.host());
        }
        if (mantle instanceof MatterMantle matter) {
            return matter;
        }
        throw new IllegalArgumentException("Built-in modules write Iris's own mantle, not " + mantle);
    }

    /** The mantle chunk at chunk coordinates {@code chunkX}, {@code chunkZ}, or null outside what the invocation reaches. */
    MantleChunk<Matter> chunk(int chunkX, int chunkZ);

    /**
     * The mantle chunk at those coordinates if the world holds it in memory, wherever it is, or null. It is never loaded
     * or created for the call and not pinned, so read it at once and do not keep it.
     */
    MantleChunk<Matter> loadedChunk(int chunkX, int chunkZ);
}
