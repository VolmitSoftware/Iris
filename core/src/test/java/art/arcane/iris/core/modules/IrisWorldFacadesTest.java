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

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.IrisSeeds;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import com.volmit.world.PackWorld;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** An Iris world gives the built-ins Iris's own seeds. */
public class IrisWorldFacadesTest {
    @Test
    public void everyIrisSeedIsTheSeedManagersSeedOfThatName() {
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getBiome()).thenReturn(1L);
        when(seeds.getCarve()).thenReturn(2L);
        when(seeds.getComplex()).thenReturn(3L);
        when(seeds.getComponent()).thenReturn(4L);
        when(seeds.getDecorator()).thenReturn(5L);
        when(seeds.getDeposit()).thenReturn(6L);
        when(seeds.getEntity()).thenReturn(7L);
        when(seeds.getLoot()).thenReturn(8L);
        when(seeds.getMantle()).thenReturn(9L);
        when(seeds.getPost()).thenReturn(10L);
        when(seeds.getTerrain()).thenReturn(11L);
        Engine engine = engine(new IrisDimension());
        when(engine.getSeedManager()).thenReturn(seeds);
        PackWorld world = new IrisPackWorld(engine);
        Map<IrisSeed, Long> expected = new EnumMap<>(IrisSeed.class);
        long next = 1;
        for (IrisSeed seed : IrisSeed.values()) {
            expected.put(seed, next++);
        }

        for (IrisSeed seed : IrisSeed.values()) {
            assertEquals(seed.name(), expected.get(seed).longValue(), world.extension(IrisSeeds.class).orElseThrow().seed(seed));
        }
    }

    private static Engine engine(IrisDimension dimension) {
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        when(engine.getDimension()).thenReturn(dimension);
        return engine;
    }
}
