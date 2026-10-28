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

import art.arcane.iris.modules.loot.BlockDrop;
import art.arcane.iris.modules.loot.BlockDropSettings;
import art.arcane.iris.builtin.loot.LootMode;
import art.arcane.iris.builtin.loot.LootSettings;
import art.arcane.iris.builtin.loot.LootSlot;
import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.iris.builtin.spawn.AmbientEffect;
import art.arcane.iris.modules.spawn.EffectSettings;
import art.arcane.iris.builtin.spawn.EntityDefinition;
import art.arcane.iris.builtin.spawn.SpawnGroup;
import art.arcane.iris.builtin.spawn.SpawnWeather;
import art.arcane.iris.builtin.spawn.Spawner;
import art.arcane.iris.modules.spawn.SpawnerSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.loot.LootResources;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import com.volmit.config.Level;
import com.volmit.block.BlockSpec;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
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
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Files of the overworld pack exactly as they were before the loot and spawn fields became module settings (copied from
 * the pack backup), decoded by the built-in modules: {@code loot} is an object, {@code blockDrops}, {@code entitySpawners}
 * and {@code effects} are lists, the loot tables, spawners and entities are the pack's own files.
 */
public class LootAndSpawnPackDecodingTest {
    private static final String PACK = "/art/arcane/iris/core/modules/pack-before-loot-and-spawn";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path packFolder;
    private LoadedPack pack;
    private ModuleSettingsDecoder decoder;

    @Before
    public void loadBuiltIns() throws IOException, URISyntaxException {
        packFolder = Path.of(LootAndSpawnPackDecodingTest.class.getResource(PACK).toURI());
        pack = LoadedPack.load(packFolder, ModuleHost.of(BuiltInPack.allBuiltIns())
                .reservingFields(ModuleTargets.reservedFields())
                .describingHostTypesIn(IrisModuleHost.HOST_TYPE_PACKAGE).copyingJarsTo(folder.newFolder("copies").toPath()));
        decoder = new ModuleSettingsDecoder(pack.catalog(), packFolder, Gson::new);
    }

    @After
    public void closePack() {
        pack.close();
    }

    @Test
    public void theDimensionReadsItsLootReference() throws IOException {
        ModuleSettings settings = decoder.decode(Level.DIMENSION, file("dimensions/overworld.json"), "dimension overworld");

        LootSettings loot = settings.find(LootSettings.class).orElseThrow();
        assertEquals(LootMode.FALLBACK, loot.mode());
        assertEquals(List.of("global-clutter"), loot.tables());
        assertEquals(1D, loot.multiplier(), 0);
        assertTrue(settings.find(BlockDropSettings.class).isEmpty());
        assertTrue(settings.find(SpawnerSettings.class).isEmpty());
    }

    @Test
    public void aRegionReadsItsLootAndItsSpawners() throws IOException {
        ModuleSettings settings = decoder.decode(Level.REGION, file("regions/terralost.json"), "region terralost");

        LootSettings loot = settings.find(LootSettings.class).orElseThrow();
        assertEquals(LootMode.FALLBACK, loot.mode());
        assertEquals(0.5, loot.multiplier(), 0);
        assertEquals(List.of("tropical/food", "cave/clutter"), loot.tables());
        assertEquals(List.of("temperate/cave", "temperate/hostile", "temperate/passive", "temperate/water"),
                settings.find(SpawnerSettings.class).orElseThrow().spawners());
    }

    @Test
    public void aRegionReadsItsBlockDrops() throws IOException {
        ModuleSettings settings = decoder.decode(Level.REGION, file("regions/swamp.json"), "region swamp");

        List<BlockDrop> drops = settings.find(BlockDropSettings.class).orElseThrow().drops();
        assertEquals(1, drops.size());
        assertEquals(List.of(BlockSpec.of("minecraft:grass_block"), BlockSpec.of("minecraft:dark_oak_leaves"),
                BlockSpec.of("minecraft:short_grass"), BlockSpec.of("minecraft:tall_grass")), drops.getFirst().blocks());
        assertEquals("slime_ball", drops.getFirst().drops().getFirst().type());
        assertEquals(24, drops.getFirst().drops().getFirst().rarity());
        assertFalse(drops.getFirst().exactBlocks());
        assertFalse(drops.getFirst().skipParents());
        assertFalse(drops.getFirst().replaceVanillaDrops());
        assertEquals(LootSlot.STORAGE, drops.getFirst().drops().getFirst().slotTypes());
    }

    @Test
    public void aRegionReadsItsEffectsLootAndBlockDropsTogether() throws IOException {
        JSONObject region = file("regions/mushroom.json");

        ModuleSettings settings = decoder.decode(Level.REGION, region, "region mushroom");

        List<AmbientEffect> effects = settings.find(EffectSettings.class).orElseThrow().effects();
        assertEquals(region.getJSONArray("effects").length(), effects.size());
        assertEquals("CRIMSON_SPORE", effects.getFirst().particleEffect());
        assertEquals(1e-05, effects.getFirst().particleAltX(), 0);
        assertEquals(1, effects.getFirst().particleAway());
        assertEquals(5, effects.getFirst().particleDistance());
        assertEquals(2, effects.getFirst().particleOffset());
        assertEquals(50, effects.getFirst().interval());
        assertEquals(3, effects.getFirst().chance());
        assertNull(effects.getFirst().sound());
        AmbientEffect sound = effects.get(1);
        assertEquals("AMBIENT.WARPED_FOREST.ADDITIONS", sound.sound());
        assertEquals(6.2, sound.volume(), 0);
        assertEquals(0.2, sound.minPitch(), 0);
        assertEquals(0.3, sound.maxPitch(), 0);
        assertEquals(3, sound.soundDistance());
        assertEquals(60000, sound.interval());
        assertEquals(8, sound.chance());
        assertEquals(2, settings.find(BlockDropSettings.class).orElseThrow().drops().getFirst().drops().size());
        assertEquals(List.of("mushroom/clutter", "mushroom/food"), settings.find(LootSettings.class).orElseThrow().tables());
        assertEquals(4, settings.find(SpawnerSettings.class).orElseThrow().spawners().size());
    }

    @Test
    public void aRegionsPotionEffectKeepsItsStrengthAndTicks() throws IOException {
        ModuleSettings settings = decoder.decode(Level.REGION, file("regions/hot.json"), "region hot");

        AmbientEffect potion = settings.find(EffectSettings.class).orElseThrow().effects().getFirst();
        assertEquals("CONFUSION", potion.potionEffect());
        assertEquals(0, potion.potionStrength());
        assertEquals(87, potion.potionTicksMin());
        assertEquals(67, potion.potionTicksMax());
        assertEquals(1200, potion.chance());
        assertEquals(500, potion.interval());
    }

    @Test
    public void aBiomeReadsItsEffects() throws IOException {
        ModuleSettings settings = decoder.decode(Level.BIOME, file("biomes/tropical/volcanoes.json"), "biome tropical/volcanoes");

        AmbientEffect smoke = settings.find(EffectSettings.class).orElseThrow().effects().getFirst();
        assertEquals("CAMPFIRE_SIGNAL_SMOKE", smoke.particleEffect());
        assertEquals(0.13885, smoke.particleAltY(), 0);
        assertEquals(300, smoke.interval());
        assertEquals(1, smoke.chance());
        assertEquals(0, smoke.particleCount());
        assertTrue(smoke.randomAltX());
        assertFalse(smoke.randomAltY());
    }

    @Test
    public void aDimensionHasNoEffects() throws IOException {
        JSONObject dimension = new JSONObject("{\"effects\": [{\"chance\": 1}], \"loot\": {}}");

        ModuleSettings settings = decoder.decode(Level.DIMENSION, dimension, "dimension overworld");

        assertTrue(settings.find(EffectSettings.class).isEmpty());
        assertFalse(decoder.keys(Level.DIMENSION).contains("effects"));
        assertTrue(decoder.keys(Level.REGION).contains("effects"));
        assertTrue(decoder.keys(Level.BIOME).containsAll(Set.of("loot", "blockDrops", "entitySpawners", "effects")));
    }

    @Test
    public void thePacksSpawnersLootTablesAndEntitiesReadTheirOwnFiles() throws IOException {
        Spawner spawner = decoder.decodeResource(Spawner.class, Files.readString(packFolder.resolve("spawners/mushroom/passive.json")));

        assertEquals(SpawnGroup.NORMAL, spawner.group());
        assertEquals(2, spawner.maxEntitiesPerChunk());
        assertEquals(SpawnWeather.NONE, spawner.weather());
        assertEquals(2_000L, spawner.maximumRate().interval());
        assertFalse(spawner.maximumRate().isInfinite());
        assertTrue(spawner.maximumRatePerChunk().isInfinite());
        assertTrue(spawner.timeBlock().contains(12));
        assertFalse(spawner.timeBlock().contains(2));
        assertEquals(0, spawner.allowedLightLevels().min(), 0);
        assertEquals(15, spawner.allowedLightLevels().max(), 0);
        assertEquals(3, spawner.spawns().size());
        assertEquals("standard/passive/mooshroom", spawner.spawns().getFirst().entity());
        assertEquals(3, spawner.spawns().getFirst().maxSpawns());
        assertEquals(3, spawner.spawns().get(1).rarity());
        assertEquals(1, spawner.spawns().get(1).minSpawns());
        assertTrue(spawner.initialSpawns().isEmpty());

        LootTable table = decoder.decodeResource(LootTable.class, Files.readString(packFolder.resolve("loot/global-clutter.json")));
        assertEquals("Global Clutter", table.name());
        assertEquals(20, table.maxTries());
        assertEquals(2, table.maxPicked());
        assertEquals("stone", table.loot().getFirst().type());
        assertEquals(8, table.loot().getFirst().maxAmount());
        assertEquals(LootSlot.STORAGE, table.loot().getFirst().slotTypes());

        EntityDefinition entity = decoder.decodeResource(EntityDefinition.class, Files.readString(packFolder.resolve("entities/standard/passive/mooshroom.json")));
        assertEquals("MOOSHROOM", entity.type());
        assertTrue(entity.aware());
        assertTrue(entity.ai());
        assertTrue(entity.gravity());
        assertTrue(entity.loot().tables().isEmpty());
        assertEquals(LootMode.ADD, entity.loot().mode());
    }

    @Test
    public void theFeatureIdsAreNotKeys() {
        JSONObject byId = new JSONObject("{\"iris:loot\": {\"tables\": [\"a\"]}, \"iris:spawn\": [\"a\"]}");

        assertSame(ModuleSettings.NONE, decoder.decode(Level.REGION, byId, "region forests"));
    }

    @Test
    public void aWrongShapeNamesTheKeyAndTheFile() {
        IllegalArgumentException list = assertThrows(IllegalArgumentException.class,
                () -> decoder.decode(Level.REGION, new JSONObject("{\"entitySpawners\": {\"spawners\": [\"a\"]}}"), "region forests"));
        IllegalArgumentException object = assertThrows(IllegalArgumentException.class,
                () -> decoder.decode(Level.REGION, new JSONObject("{\"loot\": [\"a\"]}"), "region forests"));

        assertTrue(list.getMessage(), list.getMessage().contains("under \"entitySpawners\" in region forests"));
        assertTrue(object.getMessage(), object.getMessage().contains("under \"loot\" in region forests"));
    }

    @Test
    public void settingsEncodeBackToTheJsonThePackWrote() throws IOException {
        for (String file : List.of("regions/terralost.json", "regions/swamp.json", "regions/mushroom.json", "regions/hot.json")) {
            JSONObject region = this.file(file);
            ModuleSettings settings = decoder.decode(Level.REGION, region, "region " + file);

            JSONObject written = decoder.encode(Level.REGION, settings);

            for (String key : List.of("loot", "blockDrops", "entitySpawners", "effects")) {
                assertEquals(file + " " + key, region.has(key), written.has(key));
            }
            if (region.has("entitySpawners")) {
                assertEquals(region.getJSONArray("entitySpawners").toString(), written.getJSONArray("entitySpawners").toString());
            }
            if (region.has("effects")) {
                assertEquals(region.getJSONArray("effects").length(), written.getJSONArray("effects").length());
            }
        }
    }

    @Test
    public void aLootTableOfTheWrongShapeFailsToReadSayingWhy() throws IOException {
        Path brokenPack = folder.newFolder("broken-pack").toPath();
        Files.createDirectories(brokenPack.resolve("loot"));
        Files.writeString(brokenPack.resolve("loot/broken.json"), "{\"rarity\": [1], \"loot\": [{\"type\": \"stone\"}]}");
        IrisData data = mock(IrisData.class, RETURNS_DEEP_STUBS);
        when(data.getDataFolder()).thenReturn(brokenPack.toFile());
        when(data.getModules().settings()).thenReturn(decoder);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new ModuleResourceFiles<>(data, LootResources.TABLES).read("broken"));

        assertTrue(failure.getMessage(), failure.getMessage().contains("$.rarity"));
    }

    private JSONObject file(String relative) throws IOException {
        return new JSONObject(Files.readString(packFolder.resolve(relative)));
    }
}
