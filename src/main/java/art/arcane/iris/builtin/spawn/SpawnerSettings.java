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

package art.arcane.iris.builtin.spawn;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MinItems;
import com.volmit.config.ResourceRef;

import java.util.Collections;
import java.util.List;

/**
 * The spawners one dimension, region or biome lists, written as {@code "entitySpawners": [...]}, the keys of the
 * {@link Spawner}s. A chunk may fire the spawners of its dimension, region and biome.
 */
@Desc("Entity spawners that fire in this area")
@Feature(id = "iris:spawn", name = "Entity spawners", key = "entitySpawners", root = "spawners", targets = {Level.DIMENSION, Level.REGION, Level.BIOME})
public final class SpawnerSettings implements IrisFeature {
    @MinItems(1)
    @ResourceRef(Spawner.class)
    @Desc("Entity spawners to add to this area")
    private List<String> spawners = List.of();

    public SpawnerSettings() {
    }

    public List<String> spawners() {
        return spawners == null ? List.of() : Collections.unmodifiableList(spawners);
    }
}
