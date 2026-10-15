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

import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModdedDimensionReplacementsTest {
    private static final ModdedDimensionReplacements.ReplaceableLevels STEMS = stems("aether:the_aether");

    @Test
    public void onlyLevelStemsTheServerCreatesItselfAreReplaceable() {
        ModdedDimensionReplacements.ReplaceableLevels levels = new ModdedDimensionReplacements.ReplaceableLevels(true,
                Map.of("minecraft:overworld", "minecraft:noise",
                        "examplemod:skylands", "examplemod:sky_generator",
                        "examplemod:applied", ModdedDimensionReplacements.IRIS_GENERATOR,
                        "ae2:spatial_storage", ModdedDimensionReplacements.IRIS_GENERATOR),
                Set.of("examplemod:applied"));

        assertTrue(levels.replaceable("minecraft:overworld"));
        assertTrue(levels.replaceable("minecraft:the_end"));
        assertTrue(levels.replaceable("examplemod:skylands"));
        assertTrue(levels.replaceable("examplemod:applied"));
        assertFalse(levels.replaceable("ae2:spatial_storage"));
        assertFalse(levels.replaceable("ae2:runtime_only"));
    }

    @Test
    public void withoutARecordedRegistryOnlyTheVanillaDimensionsAreReplaceable() {
        ModdedDimensionReplacements.ReplaceableLevels unknown = ModdedDimensionReplacements.ReplaceableLevels.unknown();

        assertFalse(unknown.known());
        assertTrue(unknown.replaceable("minecraft:overworld"));
        assertTrue(unknown.replaceable("minecraft:the_nether"));
        assertFalse(unknown.replaceable("examplemod:skylands"));
    }

    @Test
    public void unreplaceableLevelsGetNoLevelStemOverride() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-stage-runtime");
        try {
            Path datapack = temporary.resolve("datapack");
            ModdedDimensionReplacements.StagedTypes staged = ModdedDimensionReplacements.StagedTypes.create();
            staged.installed().put("overworld:overworld", "irisworldgen:packs/x/dimensions/y/dimension_type");

            ModdedDimensionReplacements.writeLevelStems(datapack, Map.of(
                    "ae2:spatial_storage", replacement("ae2:spatial_storage", "overworld"),
                    "minecraft:the_end", replacement("minecraft:the_end", "overworld")), staged,
                    temporary.resolve("world"), STEMS);

            assertFalse(Files.exists(datapack.resolve("data/ae2/dimension/spatial_storage.json")));
            assertTrue(Files.isRegularFile(datapack.resolve("data/minecraft/dimension/the_end.json")));
            assertEquals(Set.of("minecraft:the_end"), ModdedDimensionReplacements.stageable(Map.of(
                    "ae2:spatial_storage", replacement("ae2:spatial_storage", "overworld"),
                    "minecraft:the_end", replacement("minecraft:the_end", "overworld")), STEMS).keySet());
        } finally {
            ModdedReplacementReconcilerTest.deleteTree(temporary);
        }
    }

    @Test
    public void parsesPackAndPackDimensionReferences() {
        ModdedDimensionReplacement samePack = replacement("iristest:skylands", "skypack");
        ModdedDimensionReplacement scoped = ModdedDimensionReplacement.parse("aether:the_aether", "overworld:floating/sky",
                OptionalLong.of(42L));

        assertEquals("skypack", samePack.pack());
        assertEquals("skypack", samePack.packDimension());
        assertEquals("skypack", samePack.packRef());
        assertEquals(OptionalLong.empty(), samePack.seed());
        assertEquals("overworld", scoped.pack());
        assertEquals("floating/sky", scoped.packDimension());
        assertEquals("overworld:floating/sky", scoped.packRef());
        assertEquals(OptionalLong.of(42L), scoped.seed());
        assertEquals("aether", scoped.namespace());
        assertEquals("the_aether", scoped.path());
    }

    @Test
    public void rejectsInvalidLevelKeysAndPackReferences() {
        for (String levelKey : List.of("skylands", "Iristest:skylands", "iristest:", ":skylands", "iristest:sky/../x", "iristest:/sky")) {
            assertThrows(levelKey, IllegalArgumentException.class, () -> replacement(levelKey, "overworld"));
        }
        for (String packRef : List.of("", "  ", "../overworld", "overworld:", "over world", "overworld:a/../b")) {
            assertThrows(packRef, IllegalArgumentException.class, () -> replacement("iristest:skylands", packRef));
        }
    }

    @Test
    public void jsonRoundTripsTheTypeAndOptionalSeed() {
        ModdedDimensionReplacement seeded = ModdedDimensionReplacement.parse("minecraft:overworld", "overworld",
                OptionalLong.of(-7L));
        ModdedDimensionReplacement unseeded = replacement("examplemod:skylands", "skypack:sky");

        assertEquals("overworld", seeded.toJson().getString("type"));
        assertEquals(-7L, seeded.toJson().getLong("seed"));
        assertFalse(unseeded.toJson().has("seed"));
        assertEquals(seeded, ModdedDimensionReplacement.fromJson("minecraft:overworld", seeded.toJson()));
        assertEquals(unseeded, ModdedDimensionReplacement.fromJson("examplemod:skylands", unseeded.toJson()));
        assertThrows(IllegalArgumentException.class,
                () -> ModdedDimensionReplacement.fromJson("examplemod:skylands", "skypack"));
        assertThrows(IllegalArgumentException.class,
                () -> ModdedDimensionReplacement.fromJson("examplemod:skylands", new JSONObject("{\"seed\": 1}")));
        assertThrows(IllegalArgumentException.class,
                () -> ModdedDimensionReplacement.fromJson("examplemod:skylands",
                        new JSONObject("{\"type\": \"skypack\", \"seed\": \"abc\"}")));
    }

    @Test
    public void levelStoragePathFollowsTheDimensionsLayout() {
        Path worldRoot = Path.of("/srv/world");

        assertEquals(worldRoot.resolve("dimensions").resolve("iristest").resolve("skylands"),
                replacement("iristest:skylands", "overworld").storageFolder(worldRoot));
        assertEquals(worldRoot.resolve("dimensions").resolve("aether").resolve("realm").resolve("upper"),
                replacement("aether:realm/upper", "overworld").storageFolder(worldRoot));
        assertEquals(worldRoot.resolve("dimensions").resolve("minecraft").resolve("overworld"),
                ModdedDimensionReplacement.storageFolder(worldRoot, "minecraft:overworld"));
    }

    @Test
    public void levelStemUsesTheIrisGeneratorWithThePresetBiomeSourcePlaceholder() {
        JSONObject stem = new JSONObject(ModdedDimensionReplacements.levelStemJson("iristest:skylands",
                replacement("iristest:skylands", "overworld:sky")));

        assertEquals("iristest:skylands", stem.getString("type"));
        JSONObject generator = stem.getJSONObject("generator");
        assertEquals("irisworldgen:iris", generator.getString("type"));
        assertEquals("overworld:sky", generator.getString("dimension"));
        assertEquals("minecraft:fixed", generator.getJSONObject("biome_source").getString("type"));
        assertEquals("minecraft:plains", generator.getJSONObject("biome_source").getString("biome"));
    }

    @Test
    public void levelStemIsWrittenAtTheDatapackDimensionPathOfTheLevelKey() throws IOException {
        Path datapack = Files.createTempDirectory("iris-replace-stem");
        try {
            Path written = ModdedDimensionReplacements.writeLevelStem(datapack,
                    replacement("aether:the_aether", "overworld"), "aether:the_aether");

            assertEquals(datapack.resolve("data").resolve("aether").resolve("dimension").resolve("the_aether.json"), written);
            assertEquals("aether:the_aether", new JSONObject(Files.readString(written)).getString("type"));
        } finally {
            ModdedReplacementReconcilerTest.deleteTree(datapack);
        }
    }

    @Test
    public void changedLevelsCoverAddedChangedReseededAndRemovedReplacements() {
        Map<String, ModdedDimensionReplacement> applied = Map.of(
                "iristest:kept", seeded("iristest:kept", "overworld", 5L),
                "iristest:unseeded", seeded("iristest:unseeded", "overworld", 5L),
                "iristest:changed", seeded("iristest:changed", "overworld", 5L),
                "iristest:reseeded", seeded("iristest:reseeded", "overworld", 5L),
                "iristest:removed", seeded("iristest:removed", "overworld", 5L));
        Map<String, ModdedDimensionReplacement> configured = Map.of(
                "iristest:kept", seeded("iristest:kept", "overworld", 5L),
                "iristest:unseeded", replacement("iristest:unseeded", "overworld"),
                "iristest:changed", seeded("iristest:changed", "overworld:sky", 5L),
                "iristest:reseeded", seeded("iristest:reseeded", "overworld", 6L),
                "iristest:added", replacement("iristest:added", "overworld"));

        assertEquals(Set.of("iristest:changed", "iristest:reseeded", "iristest:removed", "iristest:added"),
                ModdedDimensionReplacements.changedLevels(applied, configured));
        assertEquals(Set.of(), ModdedDimensionReplacements.changedLevels(applied, applied));
    }

    @Test
    public void fingerprintIsOrderIndependentAndTracksEveryLevelStem() {
        Map<String, ModdedDimensionReplacement> forward = new LinkedHashMap<>();
        forward.put("iristest:a", replacement("iristest:a", "overworld"));
        forward.put("iristest:b", seeded("iristest:b", "overworld:sky", 9L));
        Map<String, ModdedDimensionReplacement> reversed = new LinkedHashMap<>();
        reversed.put("iristest:b", seeded("iristest:b", "overworld:sky", 9L));
        reversed.put("iristest:a", replacement("iristest:a", "overworld"));

        assertEquals(ModdedDimensionReplacements.fingerprint(forward), ModdedDimensionReplacements.fingerprint(reversed));
        assertNotEquals(ModdedDimensionReplacements.fingerprint(forward),
                ModdedDimensionReplacements.fingerprint(Map.of("iristest:a", replacement("iristest:a", "overworld"))));
        assertEquals("", ModdedDimensionReplacements.fingerprint(Map.of()));
        assertTrue(ModdedDimensionReplacements.fingerprint(forward).contains("iristest:b=overworld:sky"));
    }

    @Test
    public void installedPackTypeIsStagedForTheReplacement() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-stage-installed");
        try {
            Path datapack = temporary.resolve("datapack");
            ModdedDimensionReplacements.StagedTypes staged = ModdedDimensionReplacements.StagedTypes.create();
            staged.installed().put("skypack:sky", "irisworldgen:packs/x/dimensions/y/dimension_type");

            ModdedDimensionReplacements.writeLevelStems(datapack,
                    Map.of("aether:the_aether", replacement("aether:the_aether", "skypack:sky")), staged,
                    temporary.resolve("world"), STEMS);

            JSONObject stem = new JSONObject(Files.readString(datapack.resolve("data/aether/dimension/the_aether.json")));
            assertEquals("irisworldgen:packs/x/dimensions/y/dimension_type", stem.getString("type"));
            assertEquals("skypack:sky", stem.getJSONObject("generator").getString("dimension"));
        } finally {
            ModdedReplacementReconcilerTest.deleteTree(temporary);
        }
    }

    @Test
    public void retainedHistoryOfTheLevelStagesTheReplacementWithoutTheInstalledPack() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-stage-retained");
        try {
            Path datapack = temporary.resolve("datapack");
            Path worldRoot = temporary.resolve("world");
            ModdedDimensionReplacements.StagedTypes staged = ModdedDimensionReplacements.StagedTypes.create();
            staged.retained().put(worldRoot.resolve("dimensions/aether/the_aether").toAbsolutePath().normalize(),
                    "aether:the_aether");

            ModdedDimensionReplacements.writeLevelStems(datapack,
                    Map.of("aether:the_aether", replacement("aether:the_aether", "skypack:sky")), staged, worldRoot, STEMS);

            JSONObject stem = new JSONObject(Files.readString(datapack.resolve("data/aether/dimension/the_aether.json")));
            assertEquals("aether:the_aether", stem.getString("type"));
        } finally {
            ModdedReplacementReconcilerTest.deleteTree(temporary);
        }
    }

    @Test
    public void unstageableReplacementWithoutAnyRecordIsSkipped() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-stage-skip");
        try {
            Path datapack = temporary.resolve("datapack");
            Path worldRoot = temporary.resolve("world");
            Files.createDirectories(worldRoot);
            Map<String, ModdedDimensionReplacement> configured =
                    Map.of("aether:the_aether", replacement("aether:the_aether", "skypack:sky"));

            ModdedDimensionReplacements.writeLevelStems(datapack, configured,
                    ModdedDimensionReplacements.StagedTypes.create(), worldRoot, STEMS);
            ModdedDimensionReplacements.writeLevelStems(datapack, configured,
                    ModdedDimensionReplacements.StagedTypes.create(), null, STEMS);

            assertFalse(Files.exists(datapack.resolve("data/aether/dimension/the_aether.json")));
        } finally {
            ModdedReplacementReconcilerTest.deleteTree(temporary);
        }
    }

    @Test
    public void unstageableReplacementOfAnAppliedLevelRefusesStaging() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-stage-applied");
        try {
            Path worldRoot = temporary.resolve("world");
            ModdedReplacementReconciler.writeState(worldRoot, new ModdedReplacementReconciler.State(
                    Map.of("aether:the_aether", seeded("aether:the_aether", "skypack:sky", 5L)), Map.of()));

            IOException refused = assertThrows(IOException.class, () -> ModdedDimensionReplacements.writeLevelStems(
                    temporary.resolve("datapack"),
                    Map.of("aether:the_aether", replacement("aether:the_aether", "skypack:sky")),
                    ModdedDimensionReplacements.StagedTypes.create(), worldRoot, STEMS));

            assertTrue(refused.getMessage(), refused.getMessage().contains("aether:the_aether"));
            assertTrue(refused.getMessage(), refused.getMessage().contains("skypack"));
            assertTrue(refused.getMessage(), refused.getMessage().contains("dimensionReplacements"));
        } finally {
            ModdedReplacementReconcilerTest.deleteTree(temporary);
        }
    }

    @Test
    public void unstageableReplacementOfALevelWithIrisHistoryRefusesStaging() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-stage-history");
        try {
            Path worldRoot = temporary.resolve("world");
            Files.createDirectories(worldRoot.resolve("dimensions/aether/the_aether/iris/generation"));

            assertThrows(IOException.class, () -> ModdedDimensionReplacements.writeLevelStems(
                    temporary.resolve("datapack"),
                    Map.of("aether:the_aether", replacement("aether:the_aether", "skypack:sky")),
                    ModdedDimensionReplacements.StagedTypes.create(), worldRoot, STEMS));
        } finally {
            ModdedReplacementReconcilerTest.deleteTree(temporary);
        }
    }

    @Test
    public void canonicalReplacementFollowsTheInstalledPackFolderCaseAndKeepsTheSeed() throws IOException {
        Path packsRoot = Files.createTempDirectory("iris-replace-canonical");
        try {
            Files.createDirectories(packsRoot.resolve("skypack/dimensions"));
            Files.writeString(packsRoot.resolve("skypack/dimensions/sky.json"), "{}");
            boolean caseInsensitive = Files.isDirectory(packsRoot.resolve("SKYPACK"));

            Map<String, ModdedDimensionReplacement> canonical = ModdedDimensionReplacements.canonical(Map.of(
                    "aether:the_aether", seeded("aether:the_aether", "SkyPack:Sky", 3L),
                    "aether:other", replacement("aether:other", "missing:sky")), packsRoot);

            assertEquals(caseInsensitive ? "skypack:sky" : "SkyPack:Sky", canonical.get("aether:the_aether").packRef());
            assertEquals(OptionalLong.of(3L), canonical.get("aether:the_aether").seed());
            assertEquals("missing:sky", canonical.get("aether:other").packRef());
            assertTrue(ModdedDimensionReplacements.installed(replacement("aether:the_aether", "skypack:sky"), packsRoot));
            assertFalse(ModdedDimensionReplacements.installed(replacement("aether:the_aether", "skypack:void"), packsRoot));
        } finally {
            ModdedReplacementReconcilerTest.deleteTree(packsRoot);
        }
    }

    static ModdedDimensionReplacements.ReplaceableLevels stems(String... levelKeys) {
        Map<String, String> generators = new LinkedHashMap<>();
        for (String levelKey : levelKeys) {
            generators.put(levelKey, "minecraft:noise");
        }
        return new ModdedDimensionReplacements.ReplaceableLevels(true, generators, Set.of());
    }

    static ModdedDimensionReplacement replacement(String levelKey, String type) {
        return ModdedDimensionReplacement.parse(levelKey, type, OptionalLong.empty());
    }

    static ModdedDimensionReplacement seeded(String levelKey, String type, long seed) {
        return ModdedDimensionReplacement.parse(levelKey, type, OptionalLong.of(seed));
    }
}
