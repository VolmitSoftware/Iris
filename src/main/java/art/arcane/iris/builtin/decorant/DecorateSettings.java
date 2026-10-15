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
package art.arcane.iris.builtin.decorant;

import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

/** Whether a dimension decorates at all, written as {@code "decorate": false}. A dimension without the key decorates. */
@Desc("Generate decorations or not")
@Feature(id = "iris:decorant/decorate", name = "Decorate", key = "decorate", root = "decorate", targets = Target.DIMENSION)
public final class DecorateSettings implements IrisFeature {
    @Desc("Generate decorations or not")
    private boolean decorate = true;

    public DecorateSettings() {
    }

    public boolean decorate() {
        return decorate;
    }
}
