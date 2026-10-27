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

import art.arcane.iris.builtin.loot.LootSettings;
import art.arcane.iris.builtin.loot.ObjectLoot;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.ObjectHost.ObjectLoots;
import art.arcane.iris.builtin.spawn.SpawnCooldowns;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.framework.PlacedObject;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.engine.object.runtime.IrisEngineData;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.geometry.BlockPosition;
import com.volmit.world.BiomeKind;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import com.volmit.world.RunningWorld.Sky;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** What services ask a running Iris world: the engine's layout, settings and platform state, its spawners' cooldowns and the loot of its objects. */
public class IrisRunningWorldTest {
    private static final int X = 10;
    private static final int Y = 70;
    private static final int Z = -20;
    private static final int BOTTOM = -64;
    private static final int HEIGHT = 384;

    private Engine engine;
    private PackWorld world;
    private RunningWorld running;

    @BeforeClass
    public static void bindPlatform() {
        UsableBlockPlatform.bind();
    }

    @AfterClass
    public static void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Before
    public void buildWorld() {
        engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        ModuleResourceFixture.serve(data);
        ModuleResourceFixture.serveColumnSettings(data);
        when(engine.getData()).thenReturn(data);
        when(engine.getMinHeight()).thenReturn(BOTTOM);
        when(engine.getHeight()).thenReturn(HEIGHT);
        world = new EngineGeneration(engine).world();
        running = world.running().orElseThrow();
    }

    @Test
    public void theSettingsAtAColumnAreTheDimensionsTheRegionsAndTheSurfaceBiomes() {
        IrisDimension dimension = owner(IrisDimension.class, "dimension");
        when(engine.getDimension()).thenReturn(dimension);
        IrisRegion region = owner(IrisRegion.class, "region");
        IrisBiome surface = owner(IrisBiome.class, "surface");
        when(engine.getRegion(X, Z)).thenReturn(region);
        when(engine.getSurfaceBiome(X, Z)).thenReturn(surface);

        SettingsStack<LootSettings> column = running.settings().at(LootSettings.class, X, Z);

        assertEquals(List.of("dimension", "region", "surface"), List.of(tableOf(column, Level.DIMENSION), tableOf(column, Level.REGION),
                tableOf(column, Level.BIOME)));
    }

    @Test
    public void theLayoutCoversTheWholeWorldWithBiomesByTheIdsThePackGivesThem() {
        IrisBiome surface = mock(IrisBiome.class);
        when(surface.getLoadKey()).thenReturn("Plains/Meadow");
        when(surface.getInferredType()).thenReturn(InferredType.SHORE);
        when(engine.getSurfaceBiome(X, Z)).thenReturn(surface);
        when(engine.getCaveBiome(X, Y, Z)).thenReturn(surface);

        assertEquals(engine.getData().getPackResources().idOf("Plains/Meadow"), running.layout().surfaceBiome(X, Z));
        assertEquals(Optional.of(running.layout().surfaceBiome(X, Z)), running.layout().caveBiome(X, Y, Z));
        assertEquals(BiomeKind.SHORE, running.layout().surfaceKind(X, Z));
        assertTrue("far columns are in the layout", running.layout().bounds().contains(-29_999_999, 0, 29_999_999));
        assertEquals(HEIGHT, running.layout().bounds().maxY());
    }

    @Test
    public void theHighestBlockIsTheEnginesHeightNow() {
        when(engine.getHeight(X, Z, true)).thenReturn(60);
        when(engine.getHeight(X, Z, false)).thenReturn(63);

        assertEquals(60, running.highestBlock(X, Z, true));
        assertEquals(63, running.highestBlock(X, Z, false));
    }

    @Test
    public void theSkyIsTheWorldsTimeAndWeatherOnceThereIsAPlatformWorld() {
        IrisWorld platformLess = mock(IrisWorld.class);
        when(engine.getWorld()).thenReturn(platformLess);
        assertEquals(Optional.empty(), running.sky());

        PlatformWorld platform = mock(PlatformWorld.class);
        when(platform.getTime()).thenReturn(18000L);
        when(platform.isThundering()).thenReturn(true);
        when(platformLess.platformWorld()).thenReturn(platform);

        assertEquals(Optional.of(new Sky(18000L, false, true)), running.sky());
    }

    @Test
    public void caveFloorsAreHeightsAboveTheBottom() {
        EngineMantle mantle = mock(EngineMantle.class);
        when(engine.getMantle()).thenReturn(mantle);
        when(mantle.findMarkers(2, 3, art.arcane.volmlib.util.matter.slices.MarkerMatter.CAVE_FLOOR))
                .thenReturn(new KList<>(new IrisPosition(40, 0, 50), new IrisPosition(41, 30, 51)));

        assertEquals(List.of(new BlockPosition(40, 64, 50), new BlockPosition(41, 94, 51)), running.caveFloors(2, 3));
    }

    @Test
    public void aStudioWorldIsADebugWorld() {
        when(engine.isStudio()).thenReturn(true);

        assertTrue(running.debug());
    }

    @Test
    public void anObjectsLootIsWhatItsPlacementDefines() {
        ObjectPlacement placement = ObjectPlacement.builder().loot(List.of(new ObjectLoot("chest", 2))).overrideGlobalLoot(true).build();
        when(engine.getObjectPlacement(X, Y, Z)).thenReturn(new PlacedObject(placement, null, 1, X, Z));
        when(engine.getObjectPlacement(X + 1, Y, Z)).thenReturn(new PlacedObject(null, null, 1, X + 1, Z));
        ObjectHost objects = world.extension(ObjectHost.class).orElseThrow();

        ObjectLoots loots = objects.placedLoot(X, Y, Z).orElseThrow();

        assertEquals(1, loots.packTables().size());
        assertEquals("chest", loots.packTables().getFirst().name());
        assertTrue(loots.platformTables().isEmpty());
        assertTrue(loots.overridesGlobalLoot());
        assertTrue(loots.defined());
        assertTrue(objects.placedLoot(X + 1, Y, Z).isEmpty());
        assertTrue(objects.placedLoot(X + 2, Y, Z).isEmpty());
    }

    @Test
    public void spawnCooldownsCountPerWorldAndPerChunkAndForgetWhatRanOut() {
        IrisEngineData saved = new IrisEngineData();
        when(engine.getEngineData()).thenReturn(saved);
        SpawnCooldowns cooldowns = world.extension(SpawnCooldowns.class).orElseThrow();

        assertTrue(cooldowns.canSpawn("wolves", 600_000L));
        cooldowns.spawned("wolves");
        cooldowns.spawnedInChunk(3, 4, "wolves");
        cooldowns.spawned("gone");

        assertFalse(cooldowns.canSpawn("wolves", 600_000L));
        assertFalse(cooldowns.canSpawnInChunk(3, 4, "wolves", 600_000L));
        assertTrue(cooldowns.canSpawnInChunk(3, 5, "wolves", 600_000L));

        cooldowns.forgetExpired(spawner -> spawner.equals("wolves") ? 600_000L : -1L);

        assertFalse("a cooldown still running stays", cooldowns.canSpawn("wolves", 600_000L));
        assertFalse(cooldowns.canSpawnInChunk(3, 4, "wolves", 600_000L));
        assertTrue("a spawner the pack lacks is forgotten", cooldowns.canSpawn("gone", 600_000L));
    }

    private <T extends IrisRegistrant> T owner(Class<T> type, String table) {
        T owner = mock(type);
        LootSettings loot = TestSettings.of(LootSettings.class, "{\"tables\": [\"" + table + "\"]}");
        when(owner.getModuleSettings()).thenReturn(BuiltInPack.settings(loot));
        when(owner.getLoadKey()).thenReturn(table);
        return owner;
    }

    private static String tableOf(SettingsStack<LootSettings> column, Level level) {
        return column.level(level).orElseThrow().tables().getFirst();
    }
}
