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

package art.arcane.iris.engine.framework.structure.assembly;

import art.arcane.iris.engine.framework.structure.StructureAssemblyStatus;

import java.util.List;
import java.util.Objects;

/** What a {@link StructureAssembly} built: how the assembly ended, why when it did not complete, and the pieces in placement order. */
public record Assembly(StructureAssemblyStatus status, String detail, List<AssembledPiece> pieces) {
    public Assembly {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detail, "detail");
        pieces = List.copyOf(pieces);
    }

    /** Whether the assembly completed with pieces to place. */
    public boolean hasOutput() {
        return status == StructureAssemblyStatus.COMPLETE && !pieces.isEmpty();
    }
}
