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

import art.arcane.iris.modules.loot.BlockDropSettings;
import art.arcane.iris.builtin.loot.LootSettings;
import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.loot.BlockDropping;
import art.arcane.iris.engine.loot.LootResources;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import com.volmit.IrisFeature;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import com.volmit.service.BlockDrops;
import com.volmit.service.Loot;
import com.volmit.geometry.BlockPosition;
import com.volmit.world.PackWorld;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The services of the loot and spawn modules, loaded from their jars in the pack, answer about worlds Iris runs through the live view of the world. */
public class BuiltInModuleJarsServicesTest {
    private static final int X = 10;
    private static final int Y = 70;
    private static final int Z = -20;
    private static final BlockPosition AT = new BlockPosition(X, Y, Z);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Engine engine;
    private IrisBiome biome;
    private IrisDimension dimension;
    private PackWorld world;
    private ModuleResourceFixture tables;

    @Before
    public void buildWorld() {
        engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        tables = ModuleResourceFixture.serve(data);
        BlockRegistry blocks = mock(BlockRegistry.class);
        when(blocks.find(any(String.class))).thenAnswer(invocation -> java.util.Optional.of(BlockState.parse(invocation.getArgument(0))));
        when(blocks.resolve(any(BlockSpec.class))).thenAnswer(invocation -> BlockState.parse("minecraft:" + invocation.<BlockSpec>getArgument(0).block()));
        when(data.getPackResources().blocks()).thenReturn(blocks);
        when(data.getPackResources().items()).thenReturn(item -> OptionalInt.of(0));
        ModuleResourceFixture.serveColumnSettings(data);
        when(engine.getData()).thenReturn(data);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        IrisWorld engineWorld = mock(IrisWorld.class);
        when(engineWorld.minHeight()).thenReturn(-64);
        when(engine.getWorld()).thenReturn(engineWorld);
        IrisComplex complex = mock(IrisComplex.class, RETURNS_DEEP_STUBS);
        when(complex.getHeightStream().get(X, Z)).thenReturn(64D);
        when(engine.getComplex()).thenReturn(complex);
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getLoot()).thenReturn(5L);
        when(engine.getSeedManager()).thenReturn(seeds);
        biome = bare(IrisBiome.class, "biome");
        tables.biome(biome);
        dimension = bare(IrisDimension.class, "dimension");
        when(engine.getBiome(X, Y, Z)).thenReturn(biome);
        when(engine.getSurfaceBiome(X, Z)).thenReturn(biome);
        IrisRegion region = bare(IrisRegion.class, "region");
        when(engine.getRegion(X, Z)).thenReturn(region);
        when(engine.getDimension()).thenReturn(dimension);
        world = new EngineGeneration(engine).world();
    }

    @Test
    public void blockDropsOfTheBiomeAtABrokenBlockComeFromTheJarsModule() throws IOException, ClassNotFoundException {
        try (LoadedPack loaded = load()) {
            BlockDrops drops = loaded.catalog().services(BlockDrops.class).getFirst();
            when(biome.getModuleSettings()).thenReturn(BuiltInPack.settings(jarSettings(drops, BlockDropSettings.class, """
                    {"drops": [{"blocks": [{"block": "stone"}], "replaceVanillaDrops": true,
                                "drops": [{"type": "gravel", "minAmount": 2, "maxAmount": 2}]}]}""")));

            assertEquals(BlockDropping.class.getName(), drops.getClass().getName());
            assertNotSame(BlockDropping.class, drops.getClass());
            BlockDrops.Result result = drops.drops(world, new BlockDrops.Broken(AT, new BlockState("minecraft:stone")));
            assertEquals(1, result.drops().size());
            assertEquals("minecraft:gravel", result.drops().getFirst().item().toString());
            assertEquals(2, result.drops().getFirst().amount());
            assertTrue(result.replacesVanillaDrops());
            assertEquals(BlockDrops.Result.NONE, drops.drops(world, new BlockDrops.Broken(AT, new BlockState("minecraft:dirt"))));
        }
    }

    @Test
    public void aContainersTablesAreThoseOfTheDimensionAtItsColumn() throws IOException {
        when(dimension.getModuleSettings()).thenReturn(BuiltInPack.settings(TestSettings.of(LootSettings.class, "{\"tables\": [\"dungeon/chest\"]}")));
        tables.resource(LootResources.TABLES, "dungeon/chest", TestSettings.of(LootTable.class, "{}"));

        try (LoadedPack loaded = load()) {
            Loot loot = loaded.catalog().services(Loot.class).getFirst();

            List<Loot.Source> tables = loot.tables(world, new Loot.Site(AT, Optional.empty(), false, false));

            assertEquals(List.of(new Loot.Table(world.resources().idOf("dungeon/chest"))), tables);
        }
    }

    /**
     * {@code json} decoded as the jar's own copy of {@code type}, the one {@code service}'s loader holds: settings types only the jar
     * uses are not shared with Iris, so the pack's decoder hands the jar's modules values of their own classes.
     */
    private static IrisFeature jarSettings(Object service, Class<? extends IrisFeature> type, String json) throws ClassNotFoundException {
        return TestSettings.of(service.getClass().getClassLoader().loadClass(type.getName()).asSubclass(IrisFeature.class), json);
    }

    private LoadedPack load() throws IOException {
        Path pack = BuiltInPack.newPackFolder(folder);
        BuiltInModuleJars.writeInto(pack);
        return LoadedPack.load(pack, ModuleHost.of(List.of())
                .exposing(IrisModuleHost.EXPOSED_PACKAGES.toArray(String[]::new))
                .copyingJarsTo(folder.newFolder().toPath()));
    }

    private static <T extends IrisRegistrant> T bare(Class<T> type, String key) {
        T owner = mock(type);
        when(owner.getLoadKey()).thenReturn(key);
        when(owner.getModuleSettings()).thenReturn(ModuleSettings.NONE);
        return owner;
    }
}
