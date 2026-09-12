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

package art.arcane.iris.modded;

import art.arcane.iris.platform.bukkit.nms.datapack.DataVersion;
import art.arcane.iris.platform.bukkit.nms.datapack.IDataFixer;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionRuntimeContract;
import art.arcane.iris.generation.terrain.IrisDimensionTypeOptions;
import art.arcane.iris.world.IrisEnvironment;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static art.arcane.iris.generation.terrain.IrisDimensionTypeOptions.TriState.FALSE;
import static art.arcane.iris.generation.terrain.IrisDimensionTypeOptions.TriState.TRUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ModdedDimensionTypeParityTest {
    @Test
    public void everyPackDimensionUsesItsExactTypeReference() {
        IrisDimension overworld = dimension("overworld", IrisEnvironment.NORMAL, -64, 320, 320, new IrisDimensionTypeOptions());
        IrisDimension nether = dimension("nether", IrisEnvironment.NETHER, 0, 256, 256, new IrisDimensionTypeOptions());
        IrisDimension end = dimension("the_end", IrisEnvironment.THE_END, 0, 256, 256, new IrisDimensionTypeOptions());

        assertEquals("irisworldgen:packs/6f766572776f726c64/dimensions/6f766572776f726c64/dimension_type",
                ModdedWorldgenIds.dimensionTypeRef("overworld", overworld.getLoadKey()));
        assertEquals("irisworldgen:packs/6e6574686572/dimensions/6e6574686572/dimension_type",
                ModdedWorldgenIds.dimensionTypeRef("nether", nether.getLoadKey()));
        assertEquals("irisworldgen:packs/7468655f656e64/dimensions/7468655f656e64/dimension_type",
                ModdedWorldgenIds.dimensionTypeRef("the_end", end.getLoadKey()));
        assertNotEquals(ModdedWorldgenIds.dimensionTypeRef("first", "overworld"),
                ModdedWorldgenIds.dimensionTypeRef("second", "overworld"));
    }

    @Test
    public void writesExactOverworldNetherEndAndCustomContracts() throws IOException {
        IrisDimensionTypeOptions customOptions = new IrisDimensionTypeOptions()
                .coordinateScale(3.5D)
                .ambientLight(0.4F)
                .skylight(FALSE)
                .ceiling(TRUE);
        IrisDimension overworld = dimension("overworld", IrisEnvironment.NORMAL, -64, 320, 320, new IrisDimensionTypeOptions());
        IrisDimension nether = dimension("nether", IrisEnvironment.NETHER, 0, 256, 256, new IrisDimensionTypeOptions());
        IrisDimension end = dimension("the_end", IrisEnvironment.THE_END, 0, 256, 256, new IrisDimensionTypeOptions());
        IrisDimension custom = dimension("custom_contract", IrisEnvironment.CUSTOM, -128, 384, 384, customOptions);
        List<IrisDimension> dimensions = List.of(overworld, nether, end, custom);
        IDataFixer fixer = DataVersion.getLatest().get();
        Path packDirectory = Files.createTempDirectory("iris-dimension-contracts");
        KList<File> roots = new KList<>();
        roots.add(packDirectory.toFile());
        try {
            for (IrisDimension dimension : dimensions) {
                ModdedForcedDatapack.writeDimensionType(
                        roots, fixer, dimension, "contracts", dimension.getLoadKey());
                Path output = typeFile(packDirectory, "contracts", dimension);
                assertTrue(Files.isRegularFile(output));
                assertEquals(dimension.getDimensionType().toJson(fixer),
                        Files.readString(output, StandardCharsets.UTF_8));
            }

            JSONObject overworldJson = readType(packDirectory, "contracts", overworld);
            JSONObject netherJson = readType(packDirectory, "contracts", nether);
            JSONObject endJson = readType(packDirectory, "contracts", end);
            JSONObject customJson = readType(packDirectory, "contracts", custom);

            assertTrue(overworldJson.getBoolean("has_skylight"));
            assertFalse(overworldJson.getBoolean("has_ceiling"));
            assertEquals(1D, overworldJson.getDouble("coordinate_scale"), 0D);
            assertFalse(netherJson.getBoolean("has_skylight"));
            assertTrue(netherJson.getBoolean("has_ceiling"));
            assertEquals(8D, netherJson.getDouble("coordinate_scale"), 0D);
            assertTrue(endJson.getBoolean("has_ender_dragon_fight"));
            assertFalse(endJson.getBoolean("has_skylight"));
            assertEquals(-128, customJson.getInt("min_y"));
            assertEquals(512, customJson.getInt("height"));
            assertEquals(384, customJson.getInt("logical_height"));
            assertEquals(3.5D, customJson.getDouble("coordinate_scale"), 0D);
            assertEquals(0.4D, customJson.getDouble("ambient_light"), 0.000001D);
            assertFalse(customJson.getBoolean("has_skylight"));
            assertTrue(customJson.getBoolean("has_ceiling"));
        } finally {
            deleteTree(packDirectory);
        }
    }

    @Test
    public void missingExactDimensionTypeIsRejected() {
        try {
            ModdedForcedDatapack.requireRegisteredDimensionType(
                    "irisworldgen:overworld", Optional.empty(), "overworld", "overworld");
            fail("Missing dimension type must be rejected");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("irisworldgen:overworld"));
            assertTrue(e.getMessage().contains("Restart the server"));
        }
    }

    @Test
    public void loadedLevelMustMatchExactPackHeightRange() {
        IrisDimension dimension = dimension("tall", IrisEnvironment.NORMAL, -128, 384, 384, new IrisDimensionTypeOptions());
        IrisDimensionRuntimeContract contract = IrisDimensionRuntimeContract.expected(dimension, "irisworldgen");

        contract.requireHeight("test level", -128, 512);

        try {
            contract.requireHeight("test level", -256, 768);
            fail("Oversized fallback height must be rejected");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("terrain clipping is not allowed"));
        }
    }

    @Test
    public void worldCheckRejectsFallbackHeightAndSemantics() {
        IrisDimensionTypeOptions options = new IrisDimensionTypeOptions()
                .coordinateScale(2.5D)
                .ambientLight(0.3F)
                .skylight(FALSE)
                .ceiling(TRUE);
        IrisDimension dimension = dimension("runtime_contract", IrisEnvironment.CUSTOM,
                -128, 384, 384, options);
        WorldCheckDimensionContract.DimensionContract expected = WorldCheckDimensionContract.expectedDimensionContract(dimension);
        WorldCheckDimensionContract.DimensionContract fallback = new WorldCheckDimensionContract.DimensionContract(
                -256, 768, 512, 1D, 0F, true, false, false, 0);

        assertTrue(WorldCheckDimensionContract.matchesDimensionContract(-128, 512, expected, expected));
        assertFalse(WorldCheckDimensionContract.matchesDimensionContract(-256, 768, expected, fallback));
        assertFalse(WorldCheckDimensionContract.matchesDimensionContract(-128, 512, expected, fallback));
    }

    private static IrisDimension dimension(String key, IrisEnvironment environment, int minY, int maxY,
                                           int logicalHeight, IrisDimensionTypeOptions options) {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey(key);
        dimension.setEnvironment(environment);
        dimension.setDimensionHeight(new IrisRange(minY, maxY));
        dimension.setLogicalHeight(logicalHeight);
        dimension.setDimensionOptions(options);
        return dimension;
    }

    private static JSONObject readType(Path packDirectory, String pack,
                                       IrisDimension dimension) throws IOException {
        return new JSONObject(Files.readString(
                typeFile(packDirectory, pack, dimension), StandardCharsets.UTF_8));
    }

    private static Path typeFile(Path packDirectory, String pack, IrisDimension dimension) {
        String typeRef = ModdedWorldgenIds.dimensionTypeRef(pack, dimension.getLoadKey());
        return packDirectory.resolve("data/irisworldgen/dimension_type/")
                .resolve(typeRef.substring(typeRef.indexOf(':') + 1) + ".json");
    }

    private static void deleteTree(Path root) throws IOException {
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.comparingInt(Path::getNameCount).reversed()).forEach(paths::add);
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }
}
