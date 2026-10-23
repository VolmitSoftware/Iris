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

import art.arcane.iris.builtin.LiveWorld;
import art.arcane.iris.builtin.LiveWorld.BiomeKind;
import art.arcane.iris.builtin.LiveWorld.Sky;
import art.arcane.iris.builtin.spawn.Spawner;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.runtime.IrisEngineData;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.spi.PlatformWorld;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Whether a spawner may fire: the biome kind its group wants, the world's time and weather, and its rates in the engine's data. */
public class SpawnerGateTest {
    private LiveWorld live;

    @Before
    public void buildEngine() {
        Engine engine = mock(Engine.class);
        IrisWorld engineWorld = mock(IrisWorld.class);
        PlatformWorld platformWorld = mock(PlatformWorld.class);
        when(platformWorld.getTime()).thenReturn(6000L);
        when(engineWorld.platformWorld()).thenReturn(platformWorld);
        when(engine.getWorld()).thenReturn(engineWorld);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        when(engine.getEngineData()).thenReturn(new IrisEngineData());
        live = new EngineGeneration(engine).world().extension(LiveWorld.class).orElseThrow();
    }

    @Test
    public void theGroupDecidesWhichBiomesSuit() {
        Spawner normal = spawner("{\"group\": \"NORMAL\"}");
        Spawner underwater = spawner("{\"group\": \"UNDERWATER\"}");
        Spawner beach = spawner("{\"group\": \"BEACH\"}");
        Spawner cave = spawner("{\"group\": \"CAVE\"}");

        assertTrue(SpawnerGate.suits(normal, BiomeKind.LAND));
        assertFalse(SpawnerGate.suits(normal, BiomeKind.SEA));
        assertTrue(SpawnerGate.suits(underwater, BiomeKind.SEA));
        assertFalse(SpawnerGate.suits(underwater, BiomeKind.LAND));
        assertTrue(SpawnerGate.suits(beach, BiomeKind.SHORE));
        assertFalse(SpawnerGate.suits(beach, BiomeKind.CAVE));
        assertTrue(SpawnerGate.suits(cave, BiomeKind.SEA));
    }

    @Test
    public void timeAndWeatherMustMatch() {
        Spawner daytimeRain = spawner("{\"timeBlock\": {\"startHour\": 8, \"endHour\": 18}, \"weather\": \"DOWNFALL\"}");
        assertTrue(SpawnerGate.suits(daytimeRain, new Sky(6000L, true, false)));
        assertFalse(SpawnerGate.suits(daytimeRain, new Sky(6000L, false, false)));
        assertFalse(SpawnerGate.suits(daytimeRain, new Sky(20000L, true, false)));
    }

    @Test
    public void aSpawnerWithoutRatesAlwaysMayFire() {
        Spawner free = spawner("{}");

        SpawnerGate.spawned("free", free, live, 3, 4);

        assertTrue(SpawnerGate.canSpawn("free", free, live, 3, 4));
    }

    @Test
    public void aRateHoldsTheSpawnerBackForItsIntervalPerWorldAndPerChunk() {
        Spawner limited = spawner("{\"maximumRate\": {\"amount\": 1, \"per\": {\"minutes\": 10}}, \"maximumRatePerChunk\": {\"amount\": 1, \"per\": {\"minutes\": 10}}}");

        assertTrue(SpawnerGate.canSpawn("limited", limited, live, 3, 4));
        SpawnerGate.spawned("limited", limited, live, 3, 4);

        assertFalse(SpawnerGate.canSpawn("limited", limited, live, 3, 4));
        assertFalse(SpawnerGate.canSpawn("limited", limited, live, 9, 9));
        assertTrue(SpawnerGate.canSpawn("other", limited, live, 9, 9));
    }

    @Test
    public void aWorldWithoutAPlatformWorldLetsNothingFire() {
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        when(engine.getWorld()).thenReturn(mock(IrisWorld.class));

        assertFalse(SpawnerGate.canSpawn("any", spawner("{}"), new EngineGeneration(engine).world().extension(LiveWorld.class).orElseThrow(), 0, 0));
    }

    private static Spawner spawner(String json) {
        return TestSettings.of(Spawner.class, json);
    }
}
