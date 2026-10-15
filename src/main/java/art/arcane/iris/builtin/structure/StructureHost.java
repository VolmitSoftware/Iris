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
package art.arcane.iris.builtin.structure;

import com.volmit.config.PackSettings;
import com.volmit.world.PackWorld;

/**
 * What {@code iris:structure} asks of Iris: Iris places its jigsaw structures itself. The structure model (structures,
 * pools, pieces), the assembler that builds a structure from them and the locator that spreads them over the world are
 * Iris's, shared with the jigsaw studio and the structure importers and exporters. Iris's worlds implement it; a module
 * reaches it with {@link #of}.
 */
public interface StructureHost {
    /** The host of the world {@code settings} are the pack's of, which must be a world Iris generates. */
    static StructureHost of(PackSettings settings) {
        if (settings instanceof Source source) {
            return source.structures();
        }
        throw new IllegalArgumentException("Built-in modules generate Iris's own worlds, not " + settings);
    }

    /** The host of {@code world}, which must be a world Iris generates. */
    static StructureHost of(PackWorld world) {
        if (world instanceof Source source) {
            return source.structures();
        }
        throw new IllegalArgumentException("Built-in modules generate Iris's own worlds, not " + world);
    }

    /** Places the structures that start in the chunk whose mantle this thread is generating. */
    void placeChunk();

    /** How far, in blocks along X and Z, a structure may write beyond the chunk it starts in. */
    int reach();

    /** Iris's worlds answer for their structures. */
    interface Source {
        StructureHost structures();
    }
}
