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

package art.arcane.iris.core.pack;

import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.iris.builtin.spawn.EntityDefinition;
import art.arcane.iris.builtin.spawn.SpawnMarker;
import art.arcane.iris.builtin.spawn.Spawner;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInModulesJar;
import art.arcane.iris.engine.object.placement.ObjectPlacements;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.volmlib.util.json.JSONObject;
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
import static org.junit.Assert.assertTrue;

/** An export carries the loot tables, spawners, entities and markers that the pack's settings and placements reference, and what those reference. */
public class ModuleResourceClosureTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void followsEveryLevelsSettingsAndThePlacementsThroughSpawnersEntitiesAndMarkers() throws Exception {
        IrisData data = IrisData.openValidation(pack().toFile());
        try {
            ModuleResourceClosure closure = new ModuleResourceClosure(data);
            IrisDimension dimension = data.getDimensionLoader().load("main");
            IrisRegion region = data.getRegionLoader().load("region");
            IrisBiome biome = data.getBiomeLoader().load("biome");

            closure.followSettingsOf(dimension);
            closure.followSettingsOf(region);
            closure.followSettingsOf(biome);
            closure.follow(ObjectPlacements.all(biome));

            assertEquals(List.of("drops", "global", "regional"), closure.keysOf(LootTable.class));
            assertEquals(List.of("camp/zombies", "cave", "marker/spawner"), closure.keysOf(Spawner.class));
            assertEquals(List.of("beta/sentinel", "cow", "zombie"), closure.keysOf(EntityDefinition.class));
            assertEquals(List.of("camp"), closure.keysOf(SpawnMarker.class));
        } finally {
            data.close();
        }
    }

    @Test
    public void whatIsNotReferencedIsNotExportedAndMissingKeysAreSkipped() throws Exception {
        IrisData data = IrisData.openValidation(pack().toFile());
        try {
            ModuleResourceClosure closure = new ModuleResourceClosure(data);

            closure.followSettingsOf(data.getRegionLoader().load("region"));

            assertEquals(List.of("regional"), closure.keysOf(LootTable.class));
            assertEquals(List.of(), closure.keysOf(Spawner.class));
            assertFalse(closure.keysOf(LootTable.class).contains("unused"));
            assertTrue(new ModuleResourceClosure(data).isEmpty());
        } finally {
            data.close();
        }
    }

    @Test
    public void writesEachResourceUnderTheFolderOfItsKind() throws Exception {
        IrisData data = IrisData.openValidation(pack().toFile());
        try {
            ModuleResourceClosure closure = new ModuleResourceClosure(data);
            closure.followSettingsOf(data.getDimensionLoader().load("main"));
            File target = temporaryFolder.newFolder("export");

            String hashes = closure.writeTo(target, true);

            assertFalse(hashes.isEmpty());
            assertEquals("Global", new JSONObject(Files.readString(target.toPath().resolve("loot/global.json"))).getString("name"));
            assertTrue(Files.isRegularFile(target.toPath().resolve("spawners/camp/zombies.json")));
            assertTrue(Files.isRegularFile(target.toPath().resolve("entities/zombie.json")));
            assertFalse(Files.exists(target.toPath().resolve("loot/unused.json")));
        } finally {
            data.close();
        }
    }

    private Path pack() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"],\"loot\":{\"tables\":[\"global\"]},\"entitySpawners\":[\"camp/zombies\"]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"],\"loot\":{\"tables\":[\"regional\"]}}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\",\"entitySpawners\":[\"cave\"],\"blockDrops\":[{\"blocks\":[{\"block\":\"stone\"}],\"drops\":[{\"type\":\"gravel\"}]}],"
                + "\"objects\":[{\"place\":[\"hut\"],\"loot\":[{\"name\":\"drops\"}],\"markers\":[{\"marker\":\"camp\",\"mark\":[{\"block\":\"stone\"}]}]}]}");
        write(pack, "loot/global.json", "{\"name\":\"Global\",\"loot\":[{\"type\":\"stick\"}]}");
        write(pack, "loot/regional.json", "{\"name\":\"Regional\",\"loot\":[{\"type\":\"stick\"}]}");
        write(pack, "loot/drops.json", "{\"name\":\"Drops\",\"loot\":[{\"type\":\"stick\"}]}");
        write(pack, "loot/unused.json", "{\"name\":\"Unused\",\"loot\":[{\"type\":\"stick\"}]}");
        write(pack, "spawners/camp/zombies.json", "{\"spawns\":[{\"entity\":\"zombie\"}],\"initialSpawns\":[{\"entity\":\"beta/sentinel\"}]}");
        write(pack, "spawners/cave.json", "{\"spawns\":[{\"entity\":\"cow\"}]}");
        write(pack, "spawners/marker/spawner.json", "{\"spawns\":[{\"entity\":\"cow\"}]}");
        write(pack, "entities/zombie.json", "{\"type\":\"minecraft:zombie\",\"loot\":{\"tables\":[\"drops\"]}}");
        write(pack, "entities/beta/sentinel.json", "{\"type\":\"minecraft:skeleton\"}");
        write(pack, "entities/cow.json", "{\"type\":\"minecraft:cow\"}");
        write(pack, "markers/camp.json", "{\"spawners\":[\"marker/spawner\"]}");
        BuiltInModulesJar.writeInto(pack.toPath());
        return pack.toPath();
    }

    private static void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
