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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.procedural.tree.IrisTreeSettings;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import com.volmit.service.SaplingGrowth;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Trees only replace the platform's growth when the dimension asks for it and a placement of the area grows that species. */
public class TreeGrowingTest {
    private static final SaplingGrowth.Sapling OAK_SAPLING = new SaplingGrowth.Sapling(
            new BlockPosition(10, 70, 20), new BlockPosition(10, 70, 20), "TREE");

    private Engine engine;
    private IrisDimension dimension;
    private PackWorld world;

    @Before
    public void buildWorld() {
        engine = mock(Engine.class);
        dimension = mock(IrisDimension.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        world = new EngineGeneration(engine).world();
    }

    @Test
    public void aDimensionWithoutTreeOverridesLeavesTheGrowthToThePlatform() {
        when(dimension.getTreeSettings()).thenReturn(new IrisTreeSettings());

        Optional<SaplingGrowth.Growth> growth = new TreeGrowing().grow(world, OAK_SAPLING, mock(SaplingGrowth.Surroundings.class));

        assertTrue(growth.isEmpty());
        verify(engine, never()).getBiome(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void withoutAPlacementThatGrowsTheSpeciesTheGrowthIsLeftToThePlatform() {
        when(dimension.getTreeSettings()).thenReturn(new IrisTreeSettings().setEnabled(true));
        IrisBiome biome = new IrisBiome();
        biome.getObjects().add(new IrisObjectPlacement());
        when(engine.getBiome(10, 70, 20)).thenReturn(biome);
        when(engine.getRegion(10, 20)).thenReturn(new IrisRegion());

        Optional<SaplingGrowth.Growth> growth = new TreeGrowing().grow(world, OAK_SAPLING, mock(SaplingGrowth.Surroundings.class));

        assertTrue(growth.isEmpty());
    }
}
