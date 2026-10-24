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

package art.arcane.iris.modules.terrain;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

/**
 * A dimension's {@code hideOresForHiddenOre}: whether every ore block the generator would place is replaced with its base
 * material. Written as the bare boolean.
 */
@Desc("When true, every ore block the generator would place (terrain ores, deposits, ores inside objects) is replaced with its base material such as stone, deepslate or netherrack, so drop-control plugins like HiddenOre can manage ore rewards themselves")
@Feature(id = "iris:terrain/hide-ores", name = "Hide ores", key = "hideOresForHiddenOre", root = "hide", targets = Level.DIMENSION)
public final class HideOresSettings implements IrisFeature {
    @Desc("When true, every ore block the generator would place (terrain ores, deposits, ores inside objects) is replaced with its base material such as stone, deepslate or netherrack, so drop-control plugins like HiddenOre can manage ore rewards themselves")
    private boolean hide = false;

    public HideOresSettings() {
    }

    public HideOresSettings(boolean hide) {
        this.hide = hide;
    }

    public boolean hide() {
        return hide;
    }
}
