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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.modules.carve.CaveProfile;
import art.arcane.iris.core.modules.CarveTestTerrain;
import art.arcane.iris.core.modules.CarveTestWorld;
import art.arcane.iris.core.modules.InMemoryMantle;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.volmlib.util.matter.MatterCavern;
import com.volmit.config.Level;
import com.volmit.config.Range;
import com.volmit.noise.NoiseStyle;
import com.volmit.noise.StyledRange;
import com.volmit.world.BiomeSample;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The caves a chunk gets: the profile its dimension, region, surface biome and cave biome declare, the last enabled one winning, carved into the mantle. */
public class CaveCarvingTest {
    private static final int HEIGHT = 128;
    private static final int SURFACE = 100;
    private static final int FLUID_HEIGHT = 30;
    private static final int LAVA_HEIGHT = 8;
    private static final double EVERYTHING_BELOW = 2D;

    private InMemoryMantle mantle;
    private CarveTestWorld world;

    @Before
    public void setUp() {
        world = newWorld();
    }

    private static CarveTestWorld newWorld() {
        return new CarveTestWorld().biome("cave/lush").biome("plains");
    }

    @Test
    public void theDimensionsProfileCarvesWhereNothingNarrowerDeclaresOne() {
        world.dimension(profile(true));

        assertTrue(carvedBlocks(BiomeSample::surfaceBiome) > 0);
    }

    @Test
    public void aDisabledProfileDeclaresNothingAndTheDimensionsStillCarves() {
        world.dimension(profile(true));
        int dimensionOnly = carvedBlocks(BiomeSample::surfaceBiome);
        world = newWorld().dimension(profile(true)).declare(Level.REGION, "region", profile(false));

        assertTrue(dimensionOnly > 0);
        assertEquals(dimensionOnly, carvedBlocks(BiomeSample::surfaceBiome));
    }

    @Test
    public void anEnabledProfileOfARegionReplacesTheDimensionsWithinItsOwnBounds() {
        world.dimension(profile(true)).declare(Level.REGION, "region", TestSettings.with(profile(true), "verticalRange", Range.of(50D, 60D)));

        int carved = carvedBlocks(BiomeSample::surfaceBiome);

        assertTrue(carved > 0);
        mantle.chunk(0, 0).iterate(MatterCavern.class, (x, y, z, cavern) -> assertTrue("y=" + y, y >= 50 && y <= 60));
    }

    @Test
    public void theCaveBiomeOfAColumnCarvesWithItsOwnProfileWhereTheDimensionDeclaresNone() {
        world.declare(Level.BIOME, "cave/lush", profile(true));

        assertEquals("the surface biome declares none", 0, carvedBlocks(BiomeSample::surfaceBiome));
        assertTrue(carvedBlocks(sample -> sample.caveBiome().orElseThrow()) > 0);
    }

    /** Carves chunk 0, 0 with the cave biome of every block {@code caveBiomeOf} says, and counts the blocks of the chunk's mantle that became cave. */
    private int carvedBlocks(java.util.function.Function<BiomeSample, com.volmit.resource.ResourceId> caveBiomeOf) {
        mantle = new InMemoryMantle(HEIGHT / 16);
        CarveTestTerrain terrain = new CarveTestTerrain(0, 0, HEIGHT, FLUID_HEIGHT, (x, z) -> SURFACE).biomes((x, y, z) -> {
            BiomeSample sample = new BiomeSample(CarveTestWorld.id("region"), CarveTestWorld.id("plains"), Optional.of(CarveTestWorld.id("cave/lush")));
            return new BiomeSample(sample.region(), sample.surfaceBiome(), Optional.of(caveBiomeOf.apply(sample)));
        });
        CarveWorld carveWorld = new CarveWorld(world.resources().noise(), 1337L, HEIGHT, FLUID_HEIGHT, LAVA_HEIGHT, CarveTestWorld.MIN_HEIGHT);

        new CaveCarving(world, carveWorld, false, (x, z) -> false).carve(mantle.carved(), terrain, 0, 0);

        int[] count = new int[1];
        mantle.chunk(0, 0).iterate(MatterCavern.class, (x, y, z, cavern) -> count[0]++);
        return count[0];
    }

    private static CaveProfile profile(boolean enabled) {
        return TestSettings.with(new CaveProfile(), "enabled", enabled, "verticalRange", Range.of(20D, 90D),
                "densityThreshold", StyledRange.of(EVERYTHING_BELOW, EVERYTHING_BELOW, NoiseStyle.of(NoiseStyle.FLAT)),
                "allowSurfaceBreak", false, "allowFluid", false, "adaptiveSampling", false, "sampleStep", 1);
    }
}
