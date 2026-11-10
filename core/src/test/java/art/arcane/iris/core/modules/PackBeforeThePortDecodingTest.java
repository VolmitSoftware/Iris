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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.decorant.DecorantSettings;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.builtin.decorant.Decorator;
import art.arcane.iris.modules.deposit.DepositGenerator;
import art.arcane.iris.modules.deposit.DepositSettings;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.structure.StructureSettings;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.volmit.config.Level;
import com.volmit.block.BlockSpec;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import art.arcane.iris.builtin.structure.StructureTerrainMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Files of the overworld pack exactly as they were before the port (copied from the pack backup, with the snippets they
 * name), decoded by the built-in modules. Packs written for Iris before the port load unchanged: {@code deposits} and
 * {@code decorators} are lists, {@code importedStructures} an object, under the keys the modules declare.
 */
public class PackBeforeThePortDecodingTest {
    private static final String PACK = "/art/arcane/iris/core/modules/pack-before-the-port";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path packFolder;
    private LoadedPack pack;
    private ModuleSettingsDecoder decoder;

    @Before
    public void loadBuiltIns() throws IOException, URISyntaxException {
        packFolder = Path.of(PackBeforeThePortDecodingTest.class.getResource(PACK).toURI());
        pack = LoadedPack.load(packFolder, ModuleHost.of(BuiltInPack.allBuiltIns())
                .reservingFields(ModuleTargets.reservedFields())
                .describingHostTypesIn(IrisModuleHost.HOST_TYPE_PACKAGES).copyingJarsTo(folder.newFolder("copies").toPath()));
        decoder = new ModuleSettingsDecoder(pack.catalog(), packFolder, Gson::new);
    }

    @After
    public void closePack() {
        pack.close();
    }

    @Test
    public void aBiomeWithDecoratorsAndDepositsReadsBothUnderTheirOldKeys() throws IOException {
        JSONObject biome = file("biomes/mesa/red.json");

        ModuleSettings settings = decoder.decode(Level.BIOME, biome, "biome mesa/red");

        List<Decorator> decorators = settings.find(DecorantSettings.class).orElseThrow().decorators();
        assertEquals(biome.getJSONArray("decorators").length(), decorators.size());
        assertEquals(3, decorators.size());
        assertEquals(0.009, decorators.getFirst().chance(), 0);
        assertEquals(Map.of("face", "floor", "powered", "false", "facing", "east"), decorators.getFirst().palette().getFirst().data());
        assertEquals(DecorationPart.NONE, decorators.get(1).partOf());
        assertEquals(0.01, decorators.get(2).chance(), 0);
        assertEquals("minecraft:tall_dry_grass", decorators.get(2).palette().getFirst().block());

        List<DepositGenerator> deposits = settings.find(DepositSettings.class).orElseThrow().generators();
        assertEquals(1, deposits.size());
        assertEquals(100, deposits.getFirst().minHeight());
        assertEquals(150, deposits.getFirst().maxHeight());
        assertEquals(List.of(BlockSpec.of("minecraft:netherrack")), deposits.getFirst().palette());
    }

    @Test
    public void aBiomeWithManyDecoratorsAndDepositsReadsEveryEntry() throws IOException {
        JSONObject biome = file("biomes/temperate/roughplains.json");

        ModuleSettings settings = decoder.decode(Level.BIOME, biome, "biome temperate/roughplains");

        assertEquals(biome.getJSONArray("decorators").length(), settings.find(DecorantSettings.class).orElseThrow().decorators().size());
        List<DepositGenerator> deposits = settings.find(DepositSettings.class).orElseThrow().generators();
        assertEquals(biome.getJSONArray("deposits").length(), deposits.size());
        assertEquals(List.of("temperate/roughplains"), deposits.getFirst().includedBiomes());
        assertEquals(List.of(BlockSpec.of("minecraft:coal_ore")), deposits.getFirst().palette());
    }

    @Test
    public void aRegionReadsItsDeposits() throws IOException {
        JSONObject region = file("regions/magnetics.json");

        List<DepositGenerator> deposits = decoder.decode(Level.REGION, region, "region magnetics")
                .find(DepositSettings.class).orElseThrow().generators();

        assertEquals(8, deposits.size());
        assertEquals(region.getJSONArray("deposits").length(), deposits.size());
        assertEquals(224, deposits.getFirst().minHeight());
        assertEquals(736, deposits.getFirst().maxHeight());
    }

    @Test
    public void theDimensionReadsItsDepositsAndItsImportedStructures() throws IOException {
        JSONObject dimension = file("dimensions/overworld.json");

        ModuleSettings settings = decoder.decode(Level.DIMENSION, dimension, "dimension overworld");

        List<DepositGenerator> deposits = settings.find(DepositSettings.class).orElseThrow().generators();
        assertEquals(23, deposits.size());
        assertEquals(List.of(BlockSpec.of("minecraft:granite")), deposits.getFirst().palette());
        StructureSettings structures = settings.find(StructureSettings.class).orElseThrow();
        assertTrue(structures.datapackOverrides());
        assertEquals(4, structures.adjustments().size());
        assertEquals(List.of("minecraft:stronghold"), structures.adjustments().getFirst().match());
        assertEquals(StructureTerrainMode.ENCASE, structures.adjustments().getFirst().terrain().orElseThrow().resolvedMode());
        assertEquals(-64, structures.adjustments().get(1).yShift());
        assertTrue(structures.adjustments().get(2).preserveSourceY());
        assertEquals(2, structures.adjustments().get(3).stilt().orElseThrow().maxDepth());
    }

    @Test
    public void aBiomeReadsItsObjectPlacementsAndKeepsTheJsonIrisReadsThemFrom() throws IOException {
        JSONObject biome = file("biomes/temperate/roughplains.json");

        List<ObjectPlacement> objects = decoder.decode(Level.BIOME, biome, "biome temperate/roughplains")
                .find(ObjectSettings.class).orElseThrow().objects();

        assertEquals(biome.getJSONArray("objects").length(), objects.size());
        for (int index = 0; index < objects.size(); index++) {
            JSONObject written = biome.getJSONArray("objects").getJSONObject(index);
            JsonObject expected = JsonParser.parseString(written.toString()).getAsJsonObject();
            assertEquals(expected.get("place").getAsJsonArray().size(), objects.get(index).place().size());
            assertEquals(expected, JsonParser.parseString(objects.get(index).json()));
        }
    }

    @Test
    public void aBiomePlacesItsProceduralTreesLikeAnyObject() throws IOException {
        JSONObject biome = file("biomes/estranged/bloomspire-orchard.json");

        List<ObjectPlacement> objects = decoder.decode(Level.BIOME, biome, "biome estranged/bloomspire-orchard")
                .find(ObjectSettings.class).orElseThrow().objects();

        ObjectPlacement bloomspire = objects.stream()
                .filter(placement -> placement.place().equals(List.of("procedural/trees/estranged/bloomspire")))
                .findFirst().orElseThrow();
        assertEquals(0.6, bloomspire.chance(), 0);
        assertEquals(2, bloomspire.density());
        assertTrue(Files.isRegularFile(packFolder.resolve("procedural/trees/estranged/bloomspire.json")));
    }

    @Test
    public void everyTopLevelKeyOfTheseFilesIsAFieldOfIrisOrAKeyOfAModule() throws IOException {
        Map<String, Level> files = Map.of("biomes/mesa/red.json", Level.BIOME, "biomes/temperate/roughplains.json", Level.BIOME,
                "biomes/estranged/bloomspire-orchard.json", Level.BIOME, "regions/magnetics.json", Level.REGION, "dimensions/overworld.json", Level.DIMENSION);

        for (Map.Entry<String, Level> file : files.entrySet()) {
            Set<String> unknown = new TreeSet<>(file(file.getKey()).keySet());
            unknown.removeAll(ModuleTargets.reservedFields().get(file.getValue()));
            unknown.removeAll(decoder.keys(file.getValue()));
            assertEquals(file.getKey(), Set.of(), unknown);
        }
    }

    private JSONObject file(String name) throws IOException {
        return new JSONObject(Files.readString(packFolder.resolve(name)));
    }
}
