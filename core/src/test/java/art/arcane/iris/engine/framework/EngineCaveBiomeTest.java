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

package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.carve.CaveDepthSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.decorator.DecoratorTestEngines;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.util.project.stream.ProceduralStream;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.resource.ResourceId;
import com.volmit.service.CaveBiomes;
import com.volmit.world.PackWorld;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The cave biome of a position: the biome a module overrides it with, else the cave biome of the column unless the surface is nearer than that
 * biome's caves begin, else the surface biome. The overrides and the depths come from the pack's {@link CaveBiomes} provider.
 */
public class EngineCaveBiomeTest {
    private static final int MIN_HEIGHT = -64;
    private static final int SURFACE_Y = 100;

    private final IrisBiome surface = mock(IrisBiome.class);
    private final IrisBiome cave = mock(IrisBiome.class);
    private final IrisBiome band = mock(IrisBiome.class);
    private IrisData data;
    private Engine engine;

    private static final class CavesModule implements IrisModule {
        private final CaveBiomes provider;

        private CavesModule(CaveBiomes provider) {
            this.provider = provider;
        }

        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("test:caves", "Caves", "test");
        }

        @Override
        public void setup(ModuleSetup setup) {
            setup.registry().registerService(CaveBiomes.class, provider);
        }
    }

    @Before
    @SuppressWarnings("unchecked")
    public void mockEngine() {
        data = mock(IrisData.class);
        ModuleResourceFixture.serve(data);
        ResourceLoader<IrisBiome> biomes = mock(ResourceLoader.class);
        when(data.getBiomeLoader()).thenReturn(biomes);
        when(biomes.load("band/deep")).thenReturn(band);
        when(biomes.load("cave/lush")).thenReturn(cave);
        when(cave.getLoadKey()).thenReturn("cave/lush");
        engine = mock(Engine.class, CALLS_REAL_METHODS);
        doReturn(data).when(engine).getData();
        doReturn(IrisWorld.builder().minHeight(MIN_HEIGHT).maxHeight(320).build()).when(engine).getWorld();
        doReturn(surface).when(engine).getSurfaceBiome(anyInt(), anyInt());
        doReturn(cave).when(engine).getCaveBiome(anyInt(), anyInt());
        IrisComplex complex = mock(IrisComplex.class);
        @SuppressWarnings("unchecked")
        ProceduralStream<Double> heights = mock(ProceduralStream.class);
        when(heights.get(anyDouble(), anyDouble())).thenReturn((double) SURFACE_Y);
        when(complex.getHeightStream()).thenReturn(heights);
        doReturn(complex).when(engine).getComplex();
        DecoratorTestEngines.withGeneration(engine);
    }

    private void serve(CaveBiomes provider) {
        ModuleResourceFixture.serveModules(data, new CavesModule(provider));
    }

    private CaveBiomes caves(String overridden, int minDepth) {
        return new CaveBiomes() {
            @Override
            public Optional<ResourceId> overriddenBiome(PackWorld world, int x, int y, int z) {
                return Optional.ofNullable(overridden).map(world.resources()::idOf);
            }

            @Override
            public int minDepthBelowSurface(PackWorld world, ResourceId biome) {
                return biome.equals(world.resources().idOf("cave/lush")) ? minDepth : 0;
            }
        };
    }

    @Test
    public void aBiomeTheModuleOverridesWithWinsEverywhere() {
        serve(caves("band/deep", 40));

        assertSame(band, engine.getCaveBiome(10, 0, 20));
        assertSame(band, engine.getCaveBiome(10, SURFACE_Y + 30, 20));
    }

    @Test
    public void anOverrideNamingNoBiomeLeavesTheColumnsCaveBiome() {
        serve(caves("band/missing", 0));

        assertSame(cave, engine.getCaveBiome(10, 10, 20));
    }

    @Test
    public void theSurfaceBiomeHoldsAtAndAboveTheSurfaceAndWithinTheCaveBiomesMinimumDepth() {
        serve(caves(null, 10));

        assertSame("above the surface", surface, engine.getCaveBiome(10, SURFACE_Y + 1, 20));
        assertSame("at the surface", surface, engine.getCaveBiome(10, SURFACE_Y, 20));
        assertSame("nearer the surface than the caves begin", surface, engine.getCaveBiome(10, SURFACE_Y - 9, 20));
        assertSame("where the caves begin", cave, engine.getCaveBiome(10, SURFACE_Y - 10, 20));
        assertSame("deeper", cave, engine.getCaveBiome(10, 3, 20));
    }

    @Test
    public void aColumnWithoutACaveBiomeHasTheSurfaceBiome() {
        serve(caves(null, 0));
        doReturn(null).when(engine).getCaveBiome(anyInt(), anyInt());

        assertSame(surface, engine.getCaveBiome(10, 3, 20));
    }

    @Test
    public void aPackWithoutACaveBiomeProviderOverridesNothingAndLimitsNoDepth() {
        ModuleResourceFixture.serveModules(data);

        assertSame(cave, engine.getCaveBiome(10, SURFACE_Y - 1, 20));
    }

    @Test
    public void theWorldAsksTheProviderForABiomesDepthOnceAndLoadsAnOverridingBiomeOnce() {
        AtomicInteger depthQuestions = new AtomicInteger();
        CaveBiomes counting = caves(null, 10);
        serve(new CaveBiomes() {
            @Override
            public Optional<ResourceId> overriddenBiome(PackWorld world, int x, int y, int z) {
                return y > SURFACE_Y ? Optional.of(world.resources().idOf("band/deep")) : Optional.empty();
            }

            @Override
            public int minDepthBelowSurface(PackWorld world, ResourceId biome) {
                depthQuestions.incrementAndGet();
                return counting.minDepthBelowSurface(world, biome);
            }
        });

        for (int y = 0; y < SURFACE_Y; y++) {
            engine.getCaveBiome(10, y, 20);
        }
        assertSame(band, engine.getCaveBiome(10, SURFACE_Y + 5, 20));
        assertSame(band, engine.getCaveBiome(10, SURFACE_Y + 6, 20));

        assertEquals(1, depthQuestions.get());
        verify(data.getBiomeLoader(), times(1)).load("band/deep");
    }
}
