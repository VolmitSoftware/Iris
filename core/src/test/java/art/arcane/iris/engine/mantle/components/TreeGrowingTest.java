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

import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.objects.TreeGrowthSettings;
import art.arcane.iris.engine.object.TestSettings;
import com.volmit.config.FeatureSettings;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.service.SaplingGrowth;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import com.volmit.world.TerrainSampler;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Trees only replace the platform's growth when the dimension asks for it and a placement of the area grows that species. */
public class TreeGrowingTest {
    private static final SaplingGrowth.Sapling OAK_SAPLING = new SaplingGrowth.Sapling(
            new BlockPosition(10, 70, 20), new BlockPosition(10, 70, 20), ResourceId.ofPlatformKey("TREE"));
    private static final ResourceId SOURCE = ResourceId.parse("test:dimension/overworld");
    private static final ResourceId BIOME = ResourceId.parse("test:plains");
    private static final BlockBox WORLD = new BlockBox(-1000, 0, -1000, 1000, 384, 1000);

    private FeatureSettings settings;
    private TerrainSampler layout;
    private PackWorld world;

    @Before
    public void buildWorld() {
        settings = mock(FeatureSettings.class);
        layout = mock(TerrainSampler.class);
        when(layout.bounds()).thenReturn(WORLD);
        RunningWorld running = mock(RunningWorld.class);
        when(running.settings()).thenReturn(settings);
        when(running.layout()).thenReturn(layout);
        world = mock(PackWorld.class);
        when(world.extension(ObjectHost.class)).thenReturn(Optional.of(mock(ObjectHost.class)));
        when(world.running()).thenReturn(Optional.of(running));
    }

    @Test
    public void aDimensionWithoutTreeOverridesLeavesTheGrowthToThePlatform() {
        when(settings.at(TreeGrowthSettings.class, 10, 20)).thenReturn(SettingsStack.empty());

        Optional<SaplingGrowth.Growth> growth = new TreeGrowing().grow(world, OAK_SAPLING, mock(SaplingGrowth.Surroundings.class));

        assertTrue(growth.isEmpty());
        verify(layout, never()).surfaceBiome(anyInt(), anyInt());
    }

    @Test
    public void withoutAPlacementThatGrowsTheSpeciesTheGrowthIsLeftToThePlatform() {
        TreeGrowthSettings enabled = TestSettings.of(TreeGrowthSettings.class, "{\"enabled\":true}");
        when(settings.at(TreeGrowthSettings.class, 10, 20)).thenReturn(SettingsStack.of(List.of(new Entry<>(Level.DIMENSION, SOURCE, enabled))));
        ObjectSettings objects = TestSettings.with(new ObjectSettings(), "objects",
                List.of(TestSettings.of(ObjectPlacement.class, "{\"place\":[\"a\"]}")));
        when(layout.surfaceBiome(10, 20)).thenReturn(BIOME);
        when(settings.declaredBy(eq(BIOME), eq(ObjectSettings.class))).thenReturn(Optional.of(objects));
        when(settings.at(eq(ObjectSettings.class), anyInt(), anyInt())).thenReturn(SettingsStack.empty());

        Optional<SaplingGrowth.Growth> growth = new TreeGrowing().grow(world, OAK_SAPLING, mock(SaplingGrowth.Surroundings.class));

        assertTrue(growth.isEmpty());
        verify(settings).declaredBy(eq(BIOME), any());
    }
}
