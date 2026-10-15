/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.pack.ingest;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.iris.generation.decoration.IrisDepositGenerator;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.stage.IrisDepositModifier;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.ingest.catalog.CatalogBiome;
import art.arcane.iris.pack.ingest.catalog.CatalogPlacedFeature;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.KeyedBlockState;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class IngestOreHostOrderTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();
    private static final String MOD = ModIngestFixture.MOD;
    private static final int PACK_MIN_Y = -128;
    private static final int HEIGHT = 448;
    private static final int CHUNKS = 2;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Map<String, NativeBlockState> states = new HashMap<>();

    @Test
    public void hostProducingOreIsConvertedAndRunsBeforeTheOreThatConsumesItsHost() throws Exception {
        JsonArray deposits = ingestedDeposits();

        assertEquals(2, deposits.size());
        assertEquals("minecraft:sand", paletteBlock(deposits.get(0)));
        assertEquals("minecraft:clay", paletteBlock(deposits.get(1)));
        assertTrue(placed(List.of(deposits.get(0), deposits.get(1)), "minecraft:clay") > 0);
    }

    @Test
    public void consumerRunningBeforeItsHostProducerFindsNoHost() throws Exception {
        JsonArray deposits = ingestedDeposits();

        assertEquals(0, placed(List.of(deposits.get(1), deposits.get(0)), "minecraft:clay"));
    }

    private JsonArray ingestedDeposits() throws Exception {
        Path root = folder.newFolder().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        Path target = root.resolve("packs").resolve("isles");
        ModIngestReport report = ModIngester.ingest(new ModIngestRequest(source, target, "overworld", catalog(), Set.of(MOD), Set.of(), false));
        assertEquals(ModIngestTier.NATIVE, ModIngestFixture.item(report, MOD + ":isles/sand_beaches").tier());
        assertEquals(ModIngestTier.NATIVE, ModIngestFixture.item(report, MOD + ":isles/clay_patch").tier());
        return ModIngestFixture.readJson(target.resolve("biomes/ingest/" + MOD + "/sand_isles.json")).getAsJsonArray("deposits");
    }

    private static String paletteBlock(JsonElement deposit) {
        return deposit.getAsJsonObject().getAsJsonArray("palette").get(0).getAsJsonObject().get("block").getAsString();
    }

    @SuppressWarnings("unchecked")
    private int placed(List<JsonElement> depositJson, String block) {
        Gson gson = new Gson();
        IrisBiome surface = new IrisBiome();
        for (JsonElement json : depositJson) {
            ResolvedDeposit deposit = gson.fromJson(json, ResolvedDeposit.class);
            deposit.bind(states);
            surface.getDeposits().add(deposit);
        }
        Engine engine = mock(Engine.class, withSettings().stubOnly().defaultAnswer(RETURNS_DEEP_STUBS));
        ChunkContext context = mock(ChunkContext.class, withSettings().stubOnly().defaultAnswer(RETURNS_DEEP_STUBS));
        IrisRegion region = new IrisRegion();
        when(engine.getHeight()).thenReturn(HEIGHT);
        when(engine.getMinHeight()).thenReturn(PACK_MIN_Y);
        when(engine.getSeedManager().getDeposit()).thenReturn(11L);
        when(engine.getDimension()).thenReturn(new IrisDimension());
        when(context.getRoundedHeight(anyInt(), anyInt())).thenReturn(61 - PACK_MIN_Y);
        when(context.getBiome()).thenReturn(new ChunkedDataCache<>(
                ProceduralStream.of((x, z) -> surface, Interpolated.of(value -> 0D, value -> null)), 0, 0));
        when(context.getRegion()).thenReturn(new ChunkedDataCache<>(
                ProceduralStream.of((x, z) -> region, Interpolated.of(value -> 0D, value -> null)), 0, 0));
        MantleChunk<Matter> chunk = mock(MantleChunk.class, withSettings().stubOnly());
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        doReturn(chunk).when(mantle).useChunk(anyInt(), anyInt());
        when(chunk.get(anyInt(), anyInt(), anyInt(), any())).thenReturn(null);
        MultiBurst burst = new MultiBurst("Ingested deposit order", () -> 2);
        when(engine.burst()).thenReturn(burst);
        int count = 0;
        try (MockedStatic<B> blocks = mockStatic(B.class, withSettings().stubOnly().defaultAnswer(CALLS_REAL_METHODS))) {
            blocks.when(() -> B.getStateOrNull(anyString(), anyBoolean())).thenAnswer(invocation -> state(invocation.getArgument(0)));
            blocks.when(() -> B.toDeepSlateOre(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            IrisDepositModifier modifier = new IrisDepositModifier(engine);
            for (int chunkX = 0; chunkX < CHUNKS; chunkX++) {
                Hunk<NativeBlockState> terrain = islandChunk();
                modifier.generateDeposits(terrain, chunkX, 0, false, context);
                count += count(terrain, block);
            }
        } finally {
            burst.close();
        }
        return count;
    }

    private Hunk<NativeBlockState> islandChunk() {
        Hunk<NativeBlockState> terrain = Hunk.newArrayHunk(16, HEIGHT, 16);
        for (int y = 0; y < HEIGHT; y++) {
            int worldY = y + PACK_MIN_Y;
            String key = worldY < 55 ? "minecraft:stone" : worldY < 61 ? "minecraft:dirt" : worldY == 61 ? "minecraft:grass_block" : "minecraft:air";
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    terrain.set(x, y, z, state(key));
                }
            }
        }
        return terrain;
    }

    private static int count(Hunk<NativeBlockState> terrain, String block) {
        int count = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (terrain.get(x, y, z).key().equals(block)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private NativeBlockState state(String block) {
        String key = block.contains(":") ? block : "minecraft:" + block;
        return states.computeIfAbsent(key, KeyedBlockState::new);
    }

    private static WorldgenCatalog catalog() {
        WorldgenCatalog fixture = ModIngestFixture.catalog();
        List<CatalogBiome> biomes = new ArrayList<>(fixture.biomes());
        List<List<String>> steps = new ArrayList<>(11);
        for (int index = 0; index < 11; index++) {
            steps.add(index == 5 ? List.of(MOD + ":isles/clay_patch") : index == 7 ? List.of(MOD + ":isles/sand_beaches") : List.of());
        }
        biomes.add(new CatalogBiome(MOD + ":sand_isles", 0.7F, 0.8F, true, List.of("minecraft:is_overworld"), steps, List.of(), null));
        List<CatalogPlacedFeature> features = new ArrayList<>(fixture.placedFeatures());
        features.add(feature("isles/clay_patch", """
                {"targets": [{"target": {"block": "minecraft:sand", "predicate_type": "minecraft:block_match"}, "state": "minecraft:clay"}],
                 "size": 50, "discard_chance_on_air_exposure": 0.0}
                """, """
                [{"count": 2, "type": "minecraft:count"}, {"type": "minecraft:in_square"},
                 {"height": {"min_inclusive": {"absolute": 57}, "max_inclusive": {"absolute": 62}, "type": "minecraft:uniform"}, "type": "minecraft:height_range"},
                 {"type": "minecraft:biome"}]
                """));
        features.add(feature("isles/sand_beaches", """
                {"targets": [{"target": {"tag": "examplemod:soil_and_gravel", "predicate_type": "minecraft:tag_match"}, "state": "minecraft:sand"}],
                 "size": 50, "discard_chance_on_air_exposure": 0.0}
                """, """
                [{"count": 6, "type": "minecraft:count"}, {"count": 5, "type": "minecraft:count"}, {"type": "minecraft:in_square"},
                 {"noise_to_count_ratio": 1, "noise_factor": 275.0, "noise_offset": 0.2, "type": "minecraft:noise_based_count"},
                 {"height": {"min_inclusive": {"absolute": 50}, "max_inclusive": {"absolute": 61}, "type": "minecraft:uniform"}, "type": "minecraft:height_range"},
                 {"type": "minecraft:biome"}]
                """));
        Map<String, List<String>> tags = Map.of(MOD + ":soil_and_gravel", List.of("minecraft:coarse_dirt", "minecraft:dirt", "minecraft:grass_block", "minecraft:gravel"));
        return new WorldgenCatalog(fixture.format(), fixture.minecraftVersion(), fixture.platform(), fixture.capturedAtEpochMillis(),
                fixture.contentFingerprint(), fixture.namespaces(), biomes, features, fixture.structures(), fixture.dimensionTypes(),
                fixture.levelStems(), tags);
    }

    private static CatalogPlacedFeature feature(String path, String config, String placement) {
        String key = MOD + ":" + path;
        return new CatalogPlacedFeature(key, key, "minecraft:ore", JsonParser.parseString(config), JsonParser.parseString(placement));
    }

    private static final class ResolvedDeposit extends IrisDepositGenerator {
        private transient KList<NativeBlockState> resolved;

        private void bind(Map<String, NativeBlockState> states) {
            resolved = new KList<>();
            for (IrisBlockData block : getPalette()) {
                resolved.add(states.computeIfAbsent(block.getBlock(), KeyedBlockState::new));
            }
        }

        @Override
        public KList<NativeBlockState> getBlockData(IrisData data) {
            return resolved;
        }
    }
}
