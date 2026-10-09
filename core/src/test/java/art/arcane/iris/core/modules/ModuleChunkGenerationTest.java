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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.config.Feature;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModuleChunkGenerationTest {
    private static final int CHUNK_WIDTH = 16;
    private static final int HEIGHT = 4;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private LoadedPack pack;
    private Engine engine;
    private Hunk<PlatformBlockState> blocks;

    @Feature(id = "iris:pillar", name = "Pillar", targets = {Target.BIOME, Target.REGION, Target.DIMENSION})
    public static final class PillarSettings implements IrisFeature {
        private BlockState block = BlockState.parse("minecraft:stone");
        private int x;
    }

    /** Copies the block below the chunk's corner up one, and writes the configured block at {@code x}. */
    public static final class PillarModule implements IrisModule {
        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("iris:pillar", "Pillar", "1");
        }

        @Override
        public void install(StageGraph graph) {
            graph.define(Stage.chunk("iris:pillar/raise")).when(PillarSettings.class).run((context, pillars) -> {
                PillarSettings pillar = pillars.getLast().value();
                com.volmit.hunk.Hunk<BlockState> chunk = context.hunk(BlockState.class);
                chunk.set(0, 1, 0, chunk.get(0, 0, 0));
                chunk.set(pillar.x, 2, 0, pillar.block);
            });
        }
    }

    @Before
    public void loadPack() throws IOException {
        pack = LoadedPack.load(folder.newFolder("pack").toPath(),
                ModuleHost.of(List.of(new PillarModule())).copyingJarsTo(folder.newFolder("copies").toPath()));
        engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        SeedManager seeds = mock(SeedManager.class);
        when(engine.getData()).thenReturn(data);
        when(data.getDataFolder()).thenReturn(folder.getRoot());
        when(engine.getSeedManager()).thenReturn(seeds);
        when(seeds.getSeed()).thenReturn(1L);
        when(engine.getDimension()).thenReturn(new IrisDimension());
        blocks = Hunk.newArrayHunk(CHUNK_WIDTH, HEIGHT, CHUNK_WIDTH);
    }

    @After
    public void closePack() {
        pack.close();
    }

    @Test
    public void everyLevelThatConfiguresTheSettingsIsItsOwnEntryInLevelOrder() {
        List<List<String>> seen = new ArrayList<>();
        ChunkModuleSettings settings = new ChunkModuleSettings(engine, new PackResourceIds(folder.getRoot()));
        IrisRegion region = configured(new IrisRegion(), pillar(1));
        region.setLoadKey("hot");
        IrisBiome biome = configured(new IrisBiome(), pillar(2));
        biome.setLoadKey("desert/dunes");
        when(engine.getRegion(5, 6)).thenReturn(region);
        when(engine.getSurfaceBiome(5, 6)).thenReturn(biome);
        when(engine.getDimension()).thenReturn(configured(new IrisDimension(), pillar(0)));

        settings.at(PillarSettings.class, 5, 6).forEach(entry -> seen.add(List.of(entry.level().name(), entry.source().toString())));

        String pack = folder.getRoot().getName().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        assertEquals(List.of(List.of("DIMENSION", pack + ":dimension/null"), List.of("REGION", pack + ":region/hot"),
                List.of("BIOME", pack + ":biome/desert/dunes")), seen);
    }

    @Test
    public void stagesRunWhereAnyColumnOfTheChunkConfiguresThem() {
        when(engine.getRegion(anyInt(), anyInt())).thenReturn(new IrisRegion());
        IrisBiome plain = new IrisBiome();
        IrisBiome pillared = configured(new IrisBiome(), pillar(3));
        when(engine.getSurfaceBiome(anyInt(), anyInt())).thenAnswer(call -> call.getArgument(0, Integer.class) == 15 ? pillared : plain);

        ModuleChunkGeneration.generate(pack, engine, 0, 0, blocks, null, false, null, ModuleChunkGenerationTest::platformState);

        assertEquals("minecraft:oak_log[axis=y]", blocks.get(3, 2, 0).key());
    }

    @Test
    public void stagesRunWhereTheChunksBiomeConfiguresThemAndWriteIrisBlocks() {
        blocks.set(0, 0, 0, platformState("minecraft:dirt"));

        generate(configured(new IrisBiome(), pillar(3)));

        assertEquals("minecraft:dirt", blocks.get(0, 1, 0).key());
        assertEquals("minecraft:oak_log[axis=y]", blocks.get(3, 2, 0).key());
    }

    @Test
    public void unsetIrisBlocksReadAsAir() {
        generate(configured(new IrisBiome(), pillar(3)));

        assertEquals("minecraft:air", blocks.get(0, 1, 0).key());
    }

    @Test
    public void stagesStayInactiveWhereNothingConfiguresThem() {
        generate(new IrisBiome());

        assertNull(blocks.get(3, 2, 0));
    }

    @Test
    public void aBlockOutsideTheChunkFailsInsteadOfBeingClipped() {
        IndexOutOfBoundsException failure = assertThrows(IndexOutOfBoundsException.class,
                () -> generate(configured(new IrisBiome(), pillar(CHUNK_WIDTH))));

        assertEquals("Block 16, 2, 0 is outside the 16x4x16 chunk hunk", failure.getMessage());
    }

    private void generate(IrisBiome biome) {
        when(engine.getRegion(anyInt(), anyInt())).thenReturn(new IrisRegion());
        when(engine.getSurfaceBiome(anyInt(), anyInt())).thenReturn(biome);
        ModuleChunkGeneration.generate(pack, engine, 0, 0, blocks, null, false, null, ModuleChunkGenerationTest::platformState);
    }

    private static PillarSettings pillar(int x) {
        PillarSettings pillar = new PillarSettings();
        pillar.block = BlockState.parse("minecraft:oak_log[axis=y]");
        pillar.x = x;
        return pillar;
    }

    private static <T extends art.arcane.iris.core.loader.IrisRegistrant> T configured(T owner, IrisFeature settings) {
        owner.setModuleSettings(ModuleSettings.of(Map.of(settings.getClass(), settings)));
        return owner;
    }

    private static PlatformBlockState platformState(String key) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        return state;
    }

    @Test
    public void aChunkStageFindsTheSnapshotsPackResources() throws IOException {
        IrisData data = engine.getData();
        PackResourceFixture.serve(data);
        List<Object> found = new ArrayList<>();
        IrisModule finder = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:finder", "Finder", "1"); }
            public void install(StageGraph graph) {
                graph.define(Stage.chunk("test:finder/oak")).run(context ->
                        found.add(context.resources().find(PackResourceFixture.oakKey(folder.getRoot())).orElseThrow()));
            }
        };

        try (LoadedPack finderPack = LoadedPack.load(folder.newFolder("finder-pack").toPath(),
                ModuleHost.of(List.of(finder)).copyingJarsTo(folder.newFolder("finder-copies").toPath()))) {
            when(engine.getRegion(anyInt(), anyInt())).thenReturn(new IrisRegion());
            when(engine.getSurfaceBiome(anyInt(), anyInt())).thenReturn(new IrisBiome());

            ModuleChunkGeneration.generate(finderPack, engine, 0, 0, blocks, null, false, null, ModuleChunkGenerationTest::platformState);
        }

        assertEquals(List.of(PackResourceFixture.OAK), found);
    }
}
