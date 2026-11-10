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

package art.arcane.iris.core.modules;

import art.arcane.iris.engine.framework.StructureAssembler;
import art.arcane.iris.engine.object.structure.IrisStructure;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.PackResources;

/**
 * Structure assembly for Iris's own tools, the commands and studio, which run outside any module's stage, service or hook and so
 * log as Iris itself.
 */
public final class StructureAssemblies {
    private StructureAssemblies() {
    }

    /** The assembler of {@code structure}, the structure {@code structureKey} of {@code pack}, for a start at {@code origin}. */
    public static StructureAssembler forPack(PackResources pack, String structureKey, IrisStructure structure, BlockPosition origin) {
        return StructureAssembler.forPack(pack, structureKey, structure, origin, IrisModuleHost.hostLog());
    }
}
