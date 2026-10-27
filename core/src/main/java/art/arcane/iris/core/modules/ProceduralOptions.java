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

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ProceduralPlacement;

/** The options a procedural object is scattered with, as an object placement. */
public final class ProceduralOptions {
    private ProceduralOptions() {
    }

    public static ObjectPlacement of(ProceduralPlacement procedural) {
        return ObjectPlacement.builder()
                .mode(procedural.mode())
                .rotation(procedural.rotation())
                .clamp(procedural.clamp())
                .carvingSupport(procedural.carvingSupport())
                .underwater(procedural.underwater())
                .translate(procedural.translate())
                .stiltSettings(procedural.stiltSettings().orElse(null))
                .vacuumSettings(procedural.vacuumSettings().orElse(null))
                .chance(procedural.chance())
                .density(procedural.density())
                .surfaceSupportBuffer(procedural.surfaceSupportBuffer())
                .build();
    }
}
