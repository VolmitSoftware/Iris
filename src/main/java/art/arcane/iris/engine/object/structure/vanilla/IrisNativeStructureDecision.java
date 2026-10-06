/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.engine.object.structure.vanilla;

import art.arcane.iris.engine.object.structure.IrisStructureStiltSettings;
import art.arcane.iris.engine.object.structure.IrisStructureTerrain;
import art.arcane.iris.engine.object.structure.IrisStructureYBand;
public record IrisNativeStructureDecision(
        NativeStructureGenerationStatus status,
        int yShift,
        IrisStructureYBand yBand,
        boolean preserveSourceY,
        IrisStructureStiltSettings stilt,
        IrisStructureTerrain terrain
) {
    public boolean generate() {
        return status == NativeStructureGenerationStatus.GENERATE_NATIVE;
    }

    public IrisNativeStructureDecision withStatus(NativeStructureGenerationStatus replacement) {
        return new IrisNativeStructureDecision(
                replacement, yShift, yBand, preserveSourceY, stilt, terrain);
    }
}
