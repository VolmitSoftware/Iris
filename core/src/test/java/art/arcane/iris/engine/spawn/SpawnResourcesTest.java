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
import art.arcane.iris.core.modules.BuiltInModulesJar;
import art.arcane.iris.core.project.ModuleResourceSchemas;
import art.arcane.iris.core.project.SchemaTestPlatform;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.iris.builtin.spawn.EntityDefinition;
import art.arcane.iris.builtin.spawn.SpawnMarker;
import art.arcane.iris.builtin.spawn.Spawner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Spawners, entities and markers are resource kinds of module {@code iris:spawn}, read from the folders packs have always had. */
public class SpawnResourcesTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void theSpawnModuleOwnsTheKinds() throws Exception {
        IrisData data = IrisData.openValidation(spawnPack("owned").toFile());
        try {
            var catalog = data.getModules().pack().catalog();

            var kinds = catalog.resourceKinds().stream().map(registration -> registration.kind())
                    .filter(kind -> kind.id().toString().startsWith("iris:spawn/")).toList();
            assertEquals(List.of("iris:spawn/entity", "iris:spawn/marker", "iris:spawn/spawner"), kinds.stream().map(kind -> kind.id().toString()).toList());
            assertEquals(List.of("entities", "markers", "spawners"), kinds.stream().map(kind -> kind.folder()).toList());
            assertFalse(data.getLoaders().containsKey(Spawner.class));
            assertFalse(data.getLoaders().containsKey(EntityDefinition.class));
            assertFalse(data.getLoaders().containsKey(SpawnMarker.class));
        } finally {
            data.close();
        }
    }

    @Test
    public void spawnersEntitiesAndMarkersAreReadThroughPackResources() throws Exception {
        Path pack = spawnPack("spawns");
        IrisData data = IrisData.openValidation(pack.toFile());
        try {
            Spawner spawner = SpawnResources.spawner(data.getPackResources(), "camp/zombies");

            assertEquals("zombie", spawner.spawns().getFirst().entity());
            assertEquals("minecraft:zombie", SpawnResources.entity(data.getPackResources(), "zombie").type());
            assertEquals(List.of("camp/zombies"), SpawnResources.marker(data.getPackResources(), "camp").spawners());
            assertEquals(List.of(new SpawnResources.KeyedSpawner("camp/zombies", spawner)), SpawnResources.spawners(data.getPackResources(), List.of("camp/zombies", "missing")));
            assertEquals(List.of("zombie"), SpawnResources.entityKeys(data.getPackResources()));
            assertEquals(List.of("camp"), SpawnResources.markerKeys(data.getPackResources()));
            assertEquals(List.of("camp/zombies"), SpawnResources.spawnerKeys(data.getPackResources()));
            assertNull(SpawnResources.spawner(data.getPackResources(), "missing"));
            assertNull(SpawnResources.entity(data.getPackResources(), " "));

            write(pack, "entities/zombie.json", "{\"type\":\"minecraft:husk\"}");
            data.dump();
            assertEquals("minecraft:husk", SpawnResources.entity(data.getPackResources(), "zombie").type());
        } finally {
            data.close();
        }
    }

    @Test
    public void studioDescribesSpawnersEntitiesAndMarkers() throws Exception {
        Path pack = spawnPack("studio");
        IrisData data = IrisData.openValidation(pack.toFile());
        try (SchemaTestPlatform ignored = SchemaTestPlatform.bind(temporaryFolder.getRoot())) {
            List<JSONObject> entries = ModuleResourceSchemas.entries(data, true);

            for (String schema : List.of("spawners", "entities", "markers")) {
                assertTrue(schema, entries.stream().anyMatch(entry -> entry.getString("url").equals("./.iris/schema/" + schema + "-schema.json")));
                assertTrue(schema, Files.exists(pack.resolve(".iris/schema/" + schema + "-schema.json")));
            }
            JSONObject entity = new JSONObject(Files.readString(pack.resolve(".iris/schema/entities-schema.json")));
            assertTrue(entity.toString().contains("passengers"));
            assertTrue(entity.toString().contains("tables"));
        } finally {
            data.close();
        }
    }

    private Path spawnPack(String name) throws Exception {
        File pack = temporaryFolder.newFolder(name);
        write(pack.toPath(), "dimensions/main.json", "{\"regions\":[\"region\"]}");
        write(pack.toPath(), "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack.toPath(), "biomes/biome.json", "{\"name\":\"Biome\"}");
        write(pack.toPath(), "entities/zombie.json", "{\"type\":\"minecraft:zombie\"}");
        write(pack.toPath(), "spawners/camp/zombies.json", "{\"spawns\":[{\"entity\":\"zombie\"}]}");
        write(pack.toPath(), "markers/camp.json", "{\"spawners\":[\"camp/zombies\"]}");
        BuiltInModulesJar.writeInto(pack.toPath());
        return pack.toPath();
    }

    private static void write(Path root, String relative, String content) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
