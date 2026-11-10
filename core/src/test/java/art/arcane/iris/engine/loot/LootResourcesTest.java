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

package art.arcane.iris.engine.loot;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInModulesJar;
import art.arcane.iris.core.project.ModuleResourceSchemas;
import art.arcane.iris.core.project.SchemaTestPlatform;
import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.volmlib.util.json.JSONObject;
import com.volmit.host.ValidatorRegistration;
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

/** Loot tables are a resource kind of module {@code iris:loot}, read from the folder packs have always had. */
public class LootResourcesTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void theLootModuleOwnsTheKindAndItsValidator() throws Exception {
        IrisData data = IrisData.openValidation(lootPack("owned").toFile());
        try {
            var catalog = data.getModules().pack().catalog();

            assertEquals(List.of("iris:loot/table"), catalog.resourceKinds().stream().map(registration -> registration.kind().id().toString())
                    .filter(id -> id.startsWith("iris:loot/")).toList());
            assertTrue(catalog.validators().stream().map(ValidatorRegistration::validator).anyMatch(validator -> validator.getClass().getName().equals(LootValidator.class.getName())));
            assertFalse(data.getLoaders().containsKey(LootTable.class));
        } finally {
            data.close();
        }
    }

    @Test
    public void tablesAreReadThroughPackResourcesAndReadAgainAfterAReload() throws Exception {
        Path pack = lootPack("tables");
        IrisData data = IrisData.openValidation(pack.toFile());
        try {
            LootTable table = LootResources.table(data.getPackResources(), "dungeon/chest");

            assertEquals("Chest", table.name());
            assertEquals(1, table.loot().size());
            assertEquals("stick", table.loot().getFirst().type());
            assertEquals(List.of("dungeon/chest"), LootResources.tableKeys(data.getPackResources()));
            assertNull(LootResources.table(data.getPackResources(), "missing"));
            assertNull(LootResources.table(data.getPackResources(), " "));

            write(pack, "loot/dungeon/chest.json", "{\"name\":\"Renamed\"}");
            data.dump();
            assertEquals("Renamed", LootResources.table(data.getPackResources(), "dungeon/chest").name());
        } finally {
            data.close();
        }
    }

    @Test
    public void studioDescribesLootTablesAsIrisDoes() throws Exception {
        Path pack = lootPack("studio");
        IrisData data = IrisData.openValidation(pack.toFile());
        try (SchemaTestPlatform ignored = SchemaTestPlatform.bind(temporaryFolder.getRoot())) {
            List<JSONObject> entries = ModuleResourceSchemas.entries(data, true);

            assertTrue(entries.stream().anyMatch(entry -> entry.getString("url").equals("./.iris/schema/loot-schema.json")));
            assertTrue(Files.exists(pack.resolve(".iris/schema/loot-schema.json")));
        } finally {
            data.close();
        }
    }

    private Path lootPack(String name) throws Exception {
        File pack = temporaryFolder.newFolder(name);
        write(pack.toPath(), "dimensions/main.json", "{\"regions\":[{\"region\":\"region\"}]}");
        write(pack.toPath(), "regions/region.json", "{\"landBiomes\":[{\"biome\":\"biome\"}]}");
        write(pack.toPath(), "biomes/biome.json", "{\"name\":\"Biome\"}");
        write(pack.toPath(), "loot/dungeon/chest.json", "{\"name\":\"Chest\",\"loot\":[{\"type\":\"stick\"}]}");
        BuiltInModulesJar.writeInto(pack.toPath());
        return pack.toPath();
    }

    private static void write(Path root, String relative, String content) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
