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

package art.arcane.iris.modules.blocks;

import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.Level;

/** Whether a dimension shows the air smart boring writes as cobwebs, written as {@code "debugSmartBore": true}. */
@Desc("Write the air that smart boring places as cobwebs, to see where it bored.")
@Feature(id = "iris:blocks/debug-smart-bore", name = "Debug smart bore", key = "debugSmartBore", root = "show", targets = Level.DIMENSION)
public final class SmartBoreDebugSettings implements IrisFeature {
    @Desc("Write the air that smart boring places as cobwebs, to see where it bored.")
    private boolean show = false;

    public SmartBoreDebugSettings() {
    }

    public boolean show() {
        return show;
    }
}
