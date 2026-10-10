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

import art.arcane.iris.builtin.spawn.Spawner;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.runtime.IrisEngineData;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.spi.PlatformWorld;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Whether a spawner may fire: the biome kind its group wants, the world's time and weather, and its rates in the engine's data. */
public class SpawnerGateTest {
    private Engine engine;
    private PlatformWorld world;

    @Before
    public void buildEngine() {
        engine = mock(Engine.class);
        IrisWorld engineWorld = mock(IrisWorld.class);
        world = mock(PlatformWorld.class);
        when(engineWorld.platformWorld()).thenReturn(world);
        when(engine.getWorld()).thenReturn(engineWorld);
        when(engine.getEngineData()).thenReturn(new IrisEngineData());
    }

    @Test
    public void theGroupDecidesWhichBiomesSuit() {
        Spawner normal = spawner("{\"group\": \"NORMAL\"}");
        Spawner underwater = spawner("{\"group\": \"UNDERWATER\"}");
        Spawner beach = spawner("{\"group\": \"BEACH\"}");
        Spawner cave = spawner("{\"group\": \"CAVE\"}");

        assertTrue(SpawnerGate.suits(normal, biome(InferredType.LAND)));
        assertFalse(SpawnerGate.suits(normal, biome(InferredType.SEA)));
        assertTrue(SpawnerGate.suits(underwater, biome(InferredType.SEA)));
        assertFalse(SpawnerGate.suits(underwater, biome(InferredType.LAND)));
        assertTrue(SpawnerGate.suits(beach, biome(InferredType.SHORE)));
        assertFalse(SpawnerGate.suits(beach, biome(InferredType.CAVE)));
        assertTrue(SpawnerGate.suits(cave, biome(InferredType.SEA)));
    }

    @Test
    public void timeAndWeatherMustMatch() {
        Spawner daytimeRain = spawner("{\"timeBlock\": {\"startHour\": 8, \"endHour\": 18}, \"weather\": \"DOWNFALL\"}");
        when(world.getTime()).thenReturn(6000L);
        when(world.isStorming()).thenReturn(true);
        assertTrue(SpawnerGate.suits(daytimeRain, world));

        when(world.isStorming()).thenReturn(false);
        assertFalse(SpawnerGate.suits(daytimeRain, world));

        when(world.isStorming()).thenReturn(true);
        when(world.getTime()).thenReturn(20000L);
        assertFalse(SpawnerGate.suits(daytimeRain, world));
    }

    @Test
    public void aSpawnerWithoutRatesAlwaysMayFire() {
        Spawner free = spawner("{}");

        SpawnerGate.spawned("free", free, engine, 3, 4);

        assertTrue(SpawnerGate.canSpawn("free", free, engine, 3, 4));
    }

    @Test
    public void aRateHoldsTheSpawnerBackForItsIntervalPerWorldAndPerChunk() {
        Spawner limited = spawner("{\"maximumRate\": {\"amount\": 1, \"per\": {\"minutes\": 10}}, \"maximumRatePerChunk\": {\"amount\": 1, \"per\": {\"minutes\": 10}}}");

        assertTrue(SpawnerGate.canSpawn("limited", limited, engine, 3, 4));
        SpawnerGate.spawned("limited", limited, engine, 3, 4);

        assertFalse(SpawnerGate.canSpawn("limited", limited, engine, 3, 4));
        assertFalse(SpawnerGate.canSpawn("limited", limited, engine, 9, 9));
        assertTrue(SpawnerGate.canSpawn("other", limited, engine, 9, 9));
    }

    private static Spawner spawner(String json) {
        return TestSettings.of(Spawner.class, json);
    }

    private static IrisBiome biome(InferredType type) {
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getInferredType()).thenReturn(type);
        return biome;
    }
}
