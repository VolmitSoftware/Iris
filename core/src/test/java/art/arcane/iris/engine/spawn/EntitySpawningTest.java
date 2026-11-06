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
import art.arcane.iris.builtin.spawn.EntityDefinition;
import art.arcane.iris.builtin.spawn.SpawnMarker;
import art.arcane.iris.builtin.spawn.Spawner;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.spi.PlatformWorld;
import com.volmit.entity.EntitySpec;
import com.volmit.item.ItemRegistry;
import com.volmit.resource.ContentKey;
import com.volmit.resource.ResourceId;
import com.volmit.service.EntitySpawns;
import com.volmit.geometry.BlockPosition;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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

    @Rule
    public TemporaryFolder worldFolder = new TemporaryFolder();

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
        ModuleResourceFixture.serveModules(data, new SpawnModule());
        when(data.getPackResources().items()).thenReturn(mock(ItemRegistry.class));
        IrisWorld engineWorld = mock(IrisWorld.class);
        when(engineWorld.platformWorld()).thenReturn(mock(PlatformWorld.class));
        when(engine.getWorld()).thenReturn(engineWorld);
        when(engineWorld.worldFolder()).thenReturn(worldFolder.getRoot());
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getEntity()).thenReturn(11L);
        when(engine.getSeedManager()).thenReturn(seeds);
        world = new EngineGeneration(engine).world();
    }

    @Test
    public void aCaveWithoutFloorMarkersHasNowhereToSpawn() {
        RunningWorld running = mock(RunningWorld.class);
        when(running.caveFloors(2, 3)).thenReturn(List.of());

        assertNull(EntitySpawning.caveSpawnPosition(running, 2, 3));
    }

    @Test
    public void aMarkerMeansItsSpawnersAndWhetherItGoesAwayWhenItsBlockChanges() {
        SpawnMarker marker = TestSettings.of(SpawnMarker.class, "{\"spawners\": [\"zombies\", \"missing\"], \"emptyAbove\": true, \"removeOnChange\": false}");
        fixture.resource(SpawnResources.MARKERS, "camp", marker);
        fixture.resource(SpawnResources.SPAWNERS, "zombies", new Spawner());

        EntitySpawns.MarkerRule rule = new EntitySpawning().marker(world, world.resources().idOf("camp")).orElseThrow();

        assertEquals("a spawner the pack lacks is left out", List.of(world.resources().idOf("zombies")), rule.spawners());
        assertTrue(rule.emptyAbove());
        assertFalse(rule.removeOnChange());
    }

    @Test
    public void markersNoModuleKnowsAndTheCaveMarkersMeanNothing() {
        assertTrue(new EntitySpawning().marker(world, world.resources().idOf("unknown")).isEmpty());
        assertTrue(new EntitySpawning().marker(world, world.resources().idOf("cave_floor")).isEmpty());
    }

    @Test
    public void aMarkerSpawnPlansEntitiesOneBlockAboveTheMarkerWithoutSurfaceChecks() {
        Spawner spawner = TestSettings.of(Spawner.class, "{\"spawns\": [{\"entity\": \"zombie\", \"minSpawns\": 2, \"maxSpawns\": 2}]}");
        fixture.resource(SpawnResources.SPAWNERS, "camp/zombies", spawner);
        fixture.resource(SpawnResources.MARKERS, "camp", TestSettings.of(SpawnMarker.class, "{\"exhaustionChance\": 2}"));
        fixture.resource(SpawnResources.ENTITIES, "zombie", TestSettings.of(EntityDefinition.class, "{\"type\": \"minecraft:zombie\"}"));
        when(engine.getMinHeight()).thenReturn(-64);

        EntitySpawns.Plan plan = new EntitySpawning()
                .plan(world, new EntitySpawns.Marker(MARKER_BLOCK, world.resources().idOf("camp"), world.resources().idOf("camp/zombies"), false))
                .orElseThrow();

        assertEquals(world.resources().idOf("camp/zombies"), plan.spawner());
        assertTrue("an exhaustion chance above 1 always uses the marker up", plan.exhaustsMarker());
        assertEquals(2, plan.attempts().size());
        for (EntitySpawns.Attempt attempt : plan.attempts()) {
            assertEquals(new BlockPosition(40, 71, -12), attempt.at());
            assertFalse(attempt.checkSurface());
            assertEquals(vanilla("zombie"), attempt.entity().type());
        }
    }

    @Test
    public void aSpawnerThatCannotFireOrHasNoEntitiesPlansNothing() {
        fixture.resource(SpawnResources.SPAWNERS, "empty", new Spawner());

        Optional<EntitySpawns.Plan> plan = new EntitySpawning().plan(world, new EntitySpawns.Marker(MARKER_BLOCK, world.resources().idOf("camp"), world.resources().idOf("empty"), false));

        assertTrue(plan.isEmpty());
    }

    @Test
    public void anEntityOfThePackResolvesToTheSpecTheHostSpawns() {
        fixture.resource(SpawnResources.ENTITIES, "cow", TestSettings.of(EntityDefinition.class,
                "{\"type\": \"minecraft:cow\", \"customName\": \"&6Bessie\", \"baby\": true}"));

        EntitySpec spec = new EntitySpawning().entity(world, world.resources().idOf("cow")).orElseThrow();

        assertEquals(vanilla("cow"), spec.type());
        assertEquals(Optional.of("&6Bessie"), spec.appearance().customName());
        assertTrue(spec.appearance().baby());
        assertTrue(new EntitySpawning().entity(world, world.resources().idOf("nothing")).isEmpty());
    }

    @Test
    public void aMobOfAnotherProviderIsProvidedByNameWithItsSpawnReason() {
        fixture.resource(SpawnResources.ENTITIES, "king", TestSettings.of(EntityDefinition.class,
                "{\"type\": \"minecraft:zombie\", \"specialType\": \"MythicMobs:Skeleton King\", \"reason\": \"CUSTOM\"}"));

        EntitySpec spec = new EntitySpawning().entity(world, world.resources().idOf("king")).orElseThrow();

        assertEquals(new ContentKey.Provided("MythicMobs", "Skeleton King"), spec.type());
        assertEquals(Optional.of("CUSTOM"), spec.arrival().reason());
    }

    @Test
    public void anEntityWithoutAValidTypeIsNotSpawned() {
        fixture.resource(SpawnResources.ENTITIES, "broken", TestSettings.of(EntityDefinition.class, "{\"type\": \"not a key!\"}"));

        assertTrue(new EntitySpawning().entity(world, world.resources().idOf("broken")).isEmpty());
    }

    private static ContentKey vanilla(String name) {
        return new ContentKey.Registered(ResourceId.parse("minecraft:" + name));
    }
}
