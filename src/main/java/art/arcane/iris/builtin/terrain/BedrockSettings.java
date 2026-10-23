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
package art.arcane.iris.builtin.terrain;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

/** Whether a dimension has a layer of bedrock at its bottom, written as {@code "bedrock": false}. A dimension without the key has it. */
@Desc("Should bedrock be generated or not.")
@Feature(id = "iris:terrain/bedrock", name = "Bedrock", key = "bedrock", root = "bedrock", targets = Level.DIMENSION)
public final class BedrockSettings implements IrisFeature {
    @Desc("Should bedrock be generated or not.")
    private boolean bedrock = true;

    public BedrockSettings() {
    }

    public boolean bedrock() {
        return bedrock;
    }
}
