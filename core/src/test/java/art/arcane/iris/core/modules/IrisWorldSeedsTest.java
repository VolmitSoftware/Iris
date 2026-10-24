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

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import com.volmit.random.WorldRandom;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** An Iris world answers Iris's seed purposes with its legacy seeds and every other purpose by the API's rule. */
public class IrisWorldSeedsTest {
    private static final long WORLD_SEED = 42L;

    @Test
    public void everyIrisSeedPurposeIsTheSeedManagersSeedOfThatName() {
        Map<ResourceId, Long> expected = new LinkedHashMap<>();
        expected.put(IrisSeedPurposes.BIOME, 1L);
        expected.put(IrisSeedPurposes.CARVE, 2L);
        expected.put(IrisSeedPurposes.COMPLEX, 3L);
        expected.put(IrisSeedPurposes.COMPONENT, 4L);
        expected.put(IrisSeedPurposes.DECORATOR, 5L);
        expected.put(IrisSeedPurposes.DEPOSIT, 6L);
        expected.put(IrisSeedPurposes.ENTITY, 7L);
        expected.put(IrisSeedPurposes.LOOT, 8L);
        expected.put(IrisSeedPurposes.MANTLE, 9L);
        expected.put(IrisSeedPurposes.POST, 10L);
        expected.put(IrisSeedPurposes.TERRAIN, 11L);
        PackWorld world = new IrisPackWorld(engine(numberedSeeds()));

        expected.forEach((purpose, seed) -> assertEquals(purpose.toString(), seed.longValue(), world.seed(purpose)));
    }

    @Test
    public void anyOtherPurposeFollowsTheApisRule() {
        ResourceId patches = new ResourceId("example", "moss/patches");
        PackWorld world = new IrisPackWorld(engine(numberedSeeds()));

        assertEquals(WorldRandom.purposeSeed(WORLD_SEED, patches), world.seed(patches));
    }

    private static SeedManager numberedSeeds() {
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getSeed()).thenReturn(WORLD_SEED);
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
        return seeds;
    }

    private static Engine engine(SeedManager seeds) {
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        when(engine.getDimension()).thenReturn(new IrisDimension());
        when(engine.getSeedManager()).thenReturn(seeds);
        return engine;
    }
}
