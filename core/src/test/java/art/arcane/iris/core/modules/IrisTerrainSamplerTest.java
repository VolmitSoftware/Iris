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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.biome.custom.IrisBiomeCustom;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.resource.ResourceId;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisTerrainSamplerTest {
    private static final int HEIGHT = 384;
    private static final int CHUNK_REACH = 16;

    private Engine engine;
    private IrisComplex complex;

    @Before
    public void mockEngine() {
        engine = mock(Engine.class);
        complex = mock(IrisComplex.class);
        when(complex.getHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 60D + x + z));
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getHeight()).thenReturn(HEIGHT);
        when(engine.getDimension()).thenReturn(new IrisDimension());
    }

    @Test
    public void aColumnsSurfaceAloneIsTheSurfaceOfTheWholeColumn() {
        when(complex.getSlopeStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 0.5D));
        IrisTerrainSampler terrain = new IrisTerrainSampler(engine, null, 0, 0, CHUNK_REACH);

        assertEquals(terrain.column(3, -7).surfaceY(), terrain.surfaceY(3, -7), 0D);
        assertEquals(terrain.column(-16, 31).surfaceY(), terrain.surfaceY(-16, 31), 0D);
    }

    @Test
    public void aColumnsSurfaceAloneLeavesTheSlopeUnworked() {
        when(complex.getSlopeStream()).thenThrow(new AssertionError("the slope was worked out"));
        IrisTerrainSampler terrain = new IrisTerrainSampler(engine, null, 0, 0, CHUNK_REACH);

        assertEquals(64D, terrain.surfaceY(1, 3), 0D);
    }

    @Test
    public void aSkyBiomeIsTheBiomesCustomBiomeUnderTheDimensionOrItsSkyScatterPick() {
        IrisBiome custom = new IrisBiome();
        custom.setCustomDerivitives(new KList<>(List.of(new IrisBiomeCustom().setId("glow"))));
        IrisBiome scattered = new IrisBiome();
        scattered.setBiomeSkyScatter(new KList<>(List.of("cherry_grove")));
        ResourceId customId = ResourceId.parse("overworld:glowing");
        ResourceId scatteredId = ResourceId.parse("overworld:orchard");
        packBiomes(Map.of(customId, custom, scatteredId, scattered));
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("overworld");
        when(engine.getDimension()).thenReturn(dimension);
        IrisTerrainSampler terrain = new IrisTerrainSampler(engine, null, 0, 0, CHUNK_REACH);

        assertEquals(Optional.of(ResourceId.parse("overworld:glow")), terrain.skyBiome(customId, 3, 4));
        assertEquals(Optional.of(ResourceId.parse("minecraft:cherry_grove")), terrain.skyBiome(scatteredId, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> terrain.skyBiome(ResourceId.parse("overworld:missing"), 3, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> terrain.skyBiome(customId, 32, 0));
    }

    private void packBiomes(Map<ResourceId, IrisBiome> biomes) {
        IrisPackResources resources = mock(IrisPackResources.class);
        when(resources.irisBiome(any())).thenAnswer(invocation -> Optional.ofNullable(biomes.get(invocation.getArgument(0, ResourceId.class))));
        IrisData data = mock(IrisData.class);
        when(data.getPackResources()).thenReturn(resources);
        when(engine.getData()).thenReturn(data);
    }

    @Test
    public void aColumnsSurfaceOutsideTheSampledAreaIsRefusedLikeTheColumn() {
        IrisTerrainSampler terrain = new IrisTerrainSampler(engine, null, 0, 0, CHUNK_REACH);

        assertThrows(IndexOutOfBoundsException.class, () -> terrain.surfaceY(32, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> terrain.column(32, 0));
    }
}
