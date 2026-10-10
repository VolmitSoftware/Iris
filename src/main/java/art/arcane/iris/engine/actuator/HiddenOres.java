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

package art.arcane.iris.engine.actuator;

import art.arcane.iris.builtin.terrain.HideOresSettings;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;

/** Whether a dimension hides its ores ({@code hideOresForHiddenOre}): the terrain places none and the perfection pass turns the rest into their base blocks. */
public final class HiddenOres {
    private HiddenOres() {
    }

    public static boolean in(IrisDimension dimension) {
        return dimension.getModuleSettings().find(HideOresSettings.class).map(HideOresSettings::hide).orElse(false);
    }
}
