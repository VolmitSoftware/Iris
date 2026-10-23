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

package art.arcane.iris.builtin.objects;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MinItems;

import java.util.Collections;
import java.util.List;

/**
 * The object placements a region or biome declares, written as {@code "objects": [...]}, the list of placements. A chunk
 * places those of its centre column's region and of that column's biome, and of the cave biome where it places in caves.
 */
@Desc("Objects define what schematics (iob files) iris will place here")
@Feature(id = "iris:objects", name = "Objects", key = "objects", root = "objects", targets = {Level.REGION, Level.BIOME})
public final class ObjectSettings implements IrisFeature {
    @MinItems(1)
    @Desc("Objects define what schematics (iob files) iris will place here")
    private List<ObjectPlacement> objects = List.of();

    public ObjectSettings() {
    }

    public List<ObjectPlacement> objects() {
        return objects == null ? List.of() : Collections.unmodifiableList(objects);
    }
}
