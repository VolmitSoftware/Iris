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

import com.volmit.stage.MantleStageContext;
import com.volmit.resource.ResourceId;

import java.util.List;
import java.util.Optional;

/**
 * What {@code iris:structure} asks of Iris: Iris places its jigsaw structures itself. The structure model (structures,
 * pools, pieces), the assembler that builds a structure from them and the locator that spreads them over the world are
 * Iris's, shared with the jigsaw studio and the structure importers and exporters. Iris offers it as an extension of its
 * worlds and of the settings of the pack it loads: {@code world.extension(StructureHost.class)}.
 */
public interface StructureHost {
    /** Places the structures that start in the chunk whose mantle the mantle invocation {@code context} generates. */
    void placeChunk(MantleStageContext context);

    /** How far, in blocks along X and Z, a structure may write beyond the chunk it starts in. */
    int reach();

    /** The loot tables the structure gives its pieces' containers; empty when the pack has no such structure. */
    Optional<List<String>> lootTables(ResourceId structure);
}
