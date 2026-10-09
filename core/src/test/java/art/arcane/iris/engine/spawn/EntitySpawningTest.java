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

package art.arcane.iris.engine.spawn;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.entity.IrisEntity;
import art.arcane.iris.engine.object.entity.IrisEntitySpawn;
import art.arcane.iris.engine.object.entity.spawn.IrisSpawner;
import art.arcane.iris.engine.object.marker.IrisMarker;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.entity.EntitySpec;
import com.volmit.item.ItemRegistry;
import com.volmit.service.EntitySpawns;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** What the spawn module plans for markers, and what its markers mean. */
public class EntitySpawningTest {
    private static final BlockPosition MARKER_BLOCK = new BlockPosition(40, 70, -12);

    private Engine engine;
    private ModuleResourceFixture fixture;
    private PackWorld world;

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
        when(engine.getData()).thenReturn(data);
        fixture = ModuleResourceFixture.serve(data);
        when(data.getPackResources().items()).thenReturn(mock(ItemRegistry.class));
        IrisWorld engineWorld = mock(IrisWorld.class);
        when(engineWorld.platformWorld()).thenReturn(mock(PlatformWorld.class));
        when(engine.getWorld()).thenReturn(engineWorld);
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getEntity()).thenReturn(11L);
        when(engine.getSeedManager()).thenReturn(seeds);
        world = new EngineGeneration(engine).world();
    }

    @Test
    public void aCaveWithoutFloorMarkersHasNowhereToSpawn() {
        EngineMantle mantle = mock(EngineMantle.class);
        when(engine.getMantle()).thenReturn(mantle);
        when(mantle.findMarkers(2, 3, art.arcane.volmlib.util.matter.slices.MarkerMatter.CAVE_FLOOR)).thenReturn(new KList<>());

        assertNull(EntitySpawning.caveSpawnPosition(engine, 2, 3));
    }

    @Test
    public void aMarkerMeansItsSpawnersAndWhetherItGoesAwayWhenItsBlockChanges() {
        IrisMarker marker = new IrisMarker();
        marker.setSpawners(new KList<>("zombies", "missing")).setEmptyAbove(true).setRemoveOnChange(false);
        fixture.resource(SpawnResources.MARKERS, "camp", marker);
        fixture.resource(SpawnResources.SPAWNERS, "zombies", new IrisSpawner());

        EntitySpawns.MarkerRule rule = new EntitySpawning().marker(world, "camp").orElseThrow();

        assertEquals(List.of("zombies"), rule.spawners());
        assertTrue(rule.emptyAbove());
        assertFalse(rule.removeOnChange());
    }

    @Test
    public void markersNoModuleKnowsAndTheCaveMarkersMeanNothing() {
        assertTrue(new EntitySpawning().marker(world, "unknown").isEmpty());
        assertTrue(new EntitySpawning().marker(world, "cave_floor").isEmpty());
    }

    @Test
    public void aMarkerSpawnPlansEntitiesOneBlockAboveTheMarkerWithoutSurfaceChecks() {
        IrisSpawner spawner = new IrisSpawner();
        spawner.setLoadKey("camp/zombies");
        spawner.getSpawns().add(new IrisEntitySpawn().setEntity("zombie").setMinSpawns(2).setMaxSpawns(2));
        fixture.resource(SpawnResources.SPAWNERS, "camp/zombies", spawner);
        IrisMarker marker = new IrisMarker();
        marker.setExhaustionChance(2);
        fixture.resource(SpawnResources.MARKERS, "camp", marker);
        IrisEntity zombie = new IrisEntity();
        zombie.setType("minecraft:zombie");
        fixture.resource(SpawnResources.ENTITIES, "zombie", zombie);
        when(engine.getMinHeight()).thenReturn(-64);

        EntitySpawns.Plan plan = new EntitySpawning()
                .plan(world, new EntitySpawns.Marker(MARKER_BLOCK, "camp", "camp/zombies", false))
                .orElseThrow();

        assertEquals("camp/zombies", plan.spawner());
        assertTrue("an exhaustion chance above 1 always uses the marker up", plan.exhaustsMarker());
        assertEquals(2, plan.attempts().size());
        for (EntitySpawns.Attempt attempt : plan.attempts()) {
            assertEquals(new BlockPosition(40, 71, -12), attempt.at());
            assertFalse(attempt.checkSurface());
            assertEquals("minecraft:zombie", attempt.entity().type());
        }
    }

    @Test
    public void aSpawnerThatCannotFireOrHasNoEntitiesPlansNothing() {
        IrisSpawner empty = new IrisSpawner();
        fixture.resource(SpawnResources.SPAWNERS, "empty", empty);

        Optional<EntitySpawns.Plan> plan = new EntitySpawning().plan(world, new EntitySpawns.Marker(MARKER_BLOCK, "camp", "empty", false));

        assertTrue(plan.isEmpty());
    }

    @Test
    public void anEntityOfThePackResolvesToTheSpecTheHostSpawns() {
        IrisEntity entity = new IrisEntity();
        entity.setType("minecraft:cow").setCustomName("&6Bessie").setBaby(true);
        fixture.resource(SpawnResources.ENTITIES, "cow", entity);

        EntitySpec spec = new EntitySpawning().entity(world, "cow").orElseThrow();

        assertEquals("minecraft:cow", spec.type());
        assertEquals("&6Bessie", spec.customName());
        assertTrue(spec.flags().baby());
        assertTrue(new EntitySpawning().entity(world, "nothing").isEmpty());
    }
}
