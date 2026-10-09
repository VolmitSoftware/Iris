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

package art.arcane.iris.engine.spawn;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.object.entity.IrisEntity;
import art.arcane.iris.engine.object.entity.spawn.IrisSpawner;
import art.arcane.iris.engine.object.marker.IrisMarker;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;

import java.util.List;

/**
 * The resource kinds of module {@code iris:spawn}, which keep the folders and files Iris packs have always used, and
 * lookups by pack key for code that still names spawners, entities and markers by key. Every lookup goes through the
 * pack's {@link com.volmit.resource.PackResources}, so it sees the pack's current snapshot.
 * <p>
 * Lookups answer null where Iris's loaders did: for a blank key, a key the pack has no file for, and a file that fails
 * to load, which was reported when it was read.
 */
public final class SpawnResources {
    /** Spawners, in {@code spawners/}. */
    public static final ResourceKind<IrisSpawner> SPAWNERS = ResourceKind.of("iris:spawn/spawner", "spawners", IrisSpawner.class);
    /** Entities, in {@code entities/}. */
    public static final ResourceKind<IrisEntity> ENTITIES = ResourceKind.of("iris:spawn/entity", "entities", IrisEntity.class);
    /** Markers, in {@code markers/}. */
    public static final ResourceKind<IrisMarker> MARKERS = ResourceKind.of("iris:spawn/marker", "markers", IrisMarker.class);

    private SpawnResources() {
    }

    /** Every resource kind of the spawn module. */
    public static List<ResourceKind<?>> kinds() {
        return List.of(SPAWNERS, ENTITIES, MARKERS);
    }

    public static IrisSpawner spawner(IrisData data, String key) {
        return find(data, SPAWNERS, key);
    }

    public static IrisEntity entity(IrisData data, String key) {
        return find(data, ENTITIES, key);
    }

    public static IrisMarker marker(IrisData data, String key) {
        return find(data, MARKERS, key);
    }

    /** The spawners of the pack named by {@code keys}, in order, without the ones the pack lacks. */
    public static KList<IrisSpawner> spawners(IrisData data, List<String> keys) {
        KList<IrisSpawner> spawners = new KList<>();
        for (String key : keys) {
            IrisSpawner spawner = spawner(data, key);
            if (spawner != null) {
                spawners.add(spawner);
            }
        }
        return spawners;
    }

    /** The key of every entity of the pack, in key order. */
    public static List<String> entityKeys(IrisData data) {
        return keys(data, ENTITIES);
    }

    /** The key of every spawner of the pack, in key order. */
    public static List<String> spawnerKeys(IrisData data) {
        return keys(data, SPAWNERS);
    }

    /** The key of every marker of the pack, in key order. */
    public static List<String> markerKeys(IrisData data) {
        return keys(data, MARKERS);
    }

    private static <T> T find(IrisData data, ResourceKind<T> kind, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        IrisPackResources resources = data.getPackResources();
        try {
            return resources.find(kind.key(resources.idOf(key))).orElse(null);
        } catch (IllegalStateException failedToLoad) {
            return null;
        }
    }

    /** A key's id has the key as its path, so the path names the resource again wherever a key is expected. */
    private static List<String> keys(IrisData data, ResourceKind<?> kind) {
        return data.getPackResources().ids(kind.type()).stream().map(ResourceId::path).toList();
    }
}
