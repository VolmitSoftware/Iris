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

import art.arcane.iris.builtin.LiveWorld;
import art.arcane.iris.builtin.LiveWorld.BiomeKind;
import art.arcane.iris.builtin.LiveWorld.Level;
import art.arcane.iris.builtin.LiveWorld.ObjectLoots;
import art.arcane.iris.builtin.LiveWorld.Sky;
import art.arcane.iris.builtin.loot.LootSettings;
import art.arcane.iris.builtin.loot.ObjectLoot;
import art.arcane.iris.builtin.spawn.SpawnCooldowns;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.PlacedObject;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.runtime.IrisEngineData;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.geometry.BlockPosition;
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

/** What the built-in services ask a running world: the engine's answers for a block, as values. */
public class IrisLiveWorldTest {
    private static final int X = 10;
    private static final int Y = 70;
    private static final int Z = -20;
    private static final int BOTTOM = -64;

    private Engine engine;
    private LiveWorld live;

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
        when(engine.getData()).thenReturn(data);
        when(engine.getMinHeight()).thenReturn(BOTTOM);
        live = new EngineGeneration(engine).world().extension(LiveWorld.class).orElseThrow();
    }

    @Test
    public void everyLevelReadsTheSettingsOfItsOwnerAtTheBlock() {
        IrisDimension dimension = owner(IrisDimension.class, "dimension");
        IrisRegion region = owner(IrisRegion.class, "region");
        IrisBiome surface = owner(IrisBiome.class, "surface");
        IrisBiome here = owner(IrisBiome.class, "here");
        IrisBiome cave = owner(IrisBiome.class, "cave");
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getRegion(X, Z)).thenReturn(region);
        when(engine.getSurfaceBiome(X, Z)).thenReturn(surface);
        when(engine.getBiome(X, Y, Z)).thenReturn(here);
        when(engine.getCaveBiome(X, Y, Z)).thenReturn(cave);

        assertEquals(List.of("dimension", "region", "surface", "here", "cave"), List.of(
                tableAt(Level.DIMENSION), tableAt(Level.REGION), tableAt(Level.SURFACE_BIOME), tableAt(Level.BIOME), tableAt(Level.CAVE_BIOME)));
    }

    @Test
    public void aLevelWithoutSettingsOrOwnerHasNone() {
        IrisRegion bare = mock(IrisRegion.class);
        when(bare.getModuleSettings()).thenReturn(ModuleSettings.NONE);
        when(engine.getRegion(X, Z)).thenReturn(bare);

        assertTrue(live.settings(LootSettings.class, Level.REGION, X, Y, Z).isEmpty());
        assertTrue(live.settings(LootSettings.class, Level.BIOME, X, Y, Z).isEmpty());
    }

    @Test
    public void biomesAreAskedByTheIdsThePackGivesThem() {
        IrisBiome surface = mock(IrisBiome.class);
        when(surface.getLoadKey()).thenReturn("Plains/Meadow");
        when(surface.getInferredType()).thenReturn(InferredType.SHORE);
        when(engine.getSurfaceBiome(X, Z)).thenReturn(surface);
        when(engine.getCaveBiome(X, Y, Z)).thenReturn(null);

        assertEquals(engine.getData().getPackResources().idOf("Plains/Meadow"), live.surfaceBiome(X, Z));
        assertEquals(live.surfaceBiome(X, Z), live.caveBiome(X, Y, Z));
        assertEquals(BiomeKind.SHORE, live.surfaceKind(X, Z));
        assertEquals(BOTTOM, live.bottomY());
    }

    @Test
    public void theSkyIsTheWorldsTimeAndWeatherOnceThereIsAPlatformWorld() {
        IrisWorld world = mock(IrisWorld.class);
        when(engine.getWorld()).thenReturn(world);
        assertEquals(Optional.empty(), live.sky());

        PlatformWorld platform = mock(PlatformWorld.class);
        when(platform.getTime()).thenReturn(18000L);
        when(platform.isThundering()).thenReturn(true);
        when(world.platformWorld()).thenReturn(platform);

        assertEquals(Optional.of(new Sky(18000L, false, true)), live.sky());
    }

    @Test
    public void caveFloorsAreHeightsAboveTheBottom() {
        EngineMantle mantle = mock(EngineMantle.class);
        when(engine.getMantle()).thenReturn(mantle);
        when(mantle.findMarkers(2, 3, art.arcane.volmlib.util.matter.slices.MarkerMatter.CAVE_FLOOR))
                .thenReturn(new KList<>(new IrisPosition(40, 0, 50), new IrisPosition(41, 30, 51)));

        assertEquals(List.of(new BlockPosition(40, 64, 50), new BlockPosition(41, 94, 51)), live.caveFloors(2, 3));
    }

    @Test
    public void anObjectsLootIsWhatItsPlacementDefines() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.getLoot().add(new ObjectLoot("chest", 2));
        placement.setOverrideGlobalLoot(true);
        when(engine.getObjectPlacement(X, Y, Z)).thenReturn(new PlacedObject(placement, null, 1, X, Z));
        when(engine.getObjectPlacement(X + 1, Y, Z)).thenReturn(new PlacedObject(null, null, 1, X + 1, Z));

        ObjectLoots loots = live.objectLoots(X, Y, Z).orElseThrow();

        assertEquals(1, loots.packTables().size());
        assertEquals("chest", loots.packTables().getFirst().name());
        assertTrue(loots.platformTables().isEmpty());
        assertTrue(loots.overridesGlobalLoot());
        assertTrue(loots.defined());
        assertTrue(live.objectLoots(X + 1, Y, Z).isEmpty());
        assertTrue(live.objectLoots(X + 2, Y, Z).isEmpty());
    }

    @Test
    public void spawnCooldownsCountPerWorldAndPerChunkAndForgetWhatRanOut() {
        IrisEngineData saved = new IrisEngineData();
        when(engine.getEngineData()).thenReturn(saved);
        SpawnCooldowns cooldowns = live.spawnCooldowns();

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
        return owner;
    }

    private String tableAt(Level level) {
        return live.settings(LootSettings.class, level, X, Y, Z).orElseThrow().tables().getFirst();
    }
}
