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

import art.arcane.iris.builtin.spawn.EntityDefinition;
import art.arcane.iris.builtin.spawn.SpawnMarker;
import art.arcane.iris.builtin.spawn.Spawner;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;

import java.util.ArrayList;
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
    public static final ResourceKind<Spawner> SPAWNERS = ResourceKind.of("iris:spawn/spawner", "spawners", Spawner.class);
    /** Entities, in {@code entities/}. */
    public static final ResourceKind<EntityDefinition> ENTITIES = ResourceKind.of("iris:spawn/entity", "entities", EntityDefinition.class);
    /** Markers, in {@code markers/}. */
    public static final ResourceKind<SpawnMarker> MARKERS = ResourceKind.of("iris:spawn/marker", "markers", SpawnMarker.class);

    private SpawnResources() {
    }

    /** A spawner and the key the pack names it by. */
    public record KeyedSpawner(String key, Spawner spawner) {
    }

    /** Every resource kind of the spawn module. */
    public static List<ResourceKind<?>> kinds() {
        return List.of(SPAWNERS, ENTITIES, MARKERS);
    }

    public static Spawner spawner(PackResources resources, String key) {
        return find(resources, SPAWNERS, key);
    }

    public static EntityDefinition entity(PackResources resources, String key) {
        return find(resources, ENTITIES, key);
    }

    public static SpawnMarker marker(PackResources resources, String key) {
        return find(resources, MARKERS, key);
    }

    /** The spawners of the pack named by {@code keys}, in order, without the ones the pack lacks. */
    public static List<KeyedSpawner> spawners(PackResources resources, List<String> keys) {
        List<KeyedSpawner> spawners = new ArrayList<>();
        for (String key : keys) {
            Spawner spawner = spawner(resources, key);
            if (spawner != null) {
                spawners.add(new KeyedSpawner(key, spawner));
            }
        }
        return spawners;
    }

    /** The key of every entity of the pack, in key order. */
    public static List<String> entityKeys(PackResources resources) {
        return keys(resources, ENTITIES);
    }

    /** The key of every spawner of the pack, in key order. */
    public static List<String> spawnerKeys(PackResources resources) {
        return keys(resources, SPAWNERS);
    }

    /** The key of every marker of the pack, in key order. */
    public static List<String> markerKeys(PackResources resources) {
        return keys(resources, MARKERS);
    }

    private static <T> T find(PackResources resources, ResourceKind<T> kind, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return resources.find(kind.key(resources.idOf(key))).orElse(null);
        } catch (IllegalStateException failedToLoad) {
            return null;
        }
    }

    /** A key's id has the key as its path, so the path names the resource again wherever a key is expected. */
    private static List<String> keys(PackResources resources, ResourceKind<?> kind) {
        return resources.ids(kind.type()).stream().map(ResourceId::path).toList();
    }
}
