/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.terrain.floating.IrisFloatingChildBiomes;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectTranslate;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.collection.KList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MantleFloatingObjectComponentBoundaryRadiusTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        // IrisObject retains these built-in states after this fixture unbinds its platform.
        Map<String, PlatformBlockState> registryStates = new HashMap<>();
        when(registries.block(anyString())).thenAnswer(invocation -> {
            String key = invocation.<String>getArgument(0).toLowerCase(java.util.Locale.ROOT);
            return registryStates.computeIfAbsent(key, value -> {
                PlatformBlockState block = mock(PlatformBlockState.class);
                when(block.key()).thenReturn(value);
                when(block.materialKey()).thenReturn(value.split("\\[", 2)[0]);
                when(block.isSolid()).thenReturn(!value.contains("air"));
                when(block.isOccluding()).thenReturn(!value.contains("air"));
                return block;
            });
        });
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void transformedFloatingPlacementExpandsOwnerRadius() throws Exception {
        File objectFile = temporaryFolder.newFile("floating.iob");
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(objectFile))) {
            output.writeInt(1);
            output.writeInt(1);
            output.writeInt(1);
        }

        IrisObjectPlacement placement = new IrisObjectPlacement()
                .setPlace(new KList<>("test/floating"))
                .setTranslate(new IrisObjectTranslate().setX(32));
        IrisFloatingChildBiomes floatingChild = new IrisFloatingChildBiomes()
                .setFloatingObjects(new KList<>(placement));
        IrisBiome biome = new IrisBiome().setFloatingChildBiomes(new KList<>(floatingChild));
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getReachableBiomes(org.mockito.ArgumentMatchers.any())).thenReturn(new KList<>(biome));

        ResourceLoader<IrisObject> objectLoader = mock(ResourceLoader.class);
        when(objectLoader.findFile("test/floating")).thenReturn(objectFile);
        IrisData data = mock(IrisData.class);
        when(data.getObjectLoader()).thenReturn(objectLoader);

        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getEngine()).thenReturn(engine);
        when(engineMantle.getData()).thenReturn(data);

        assertEquals(33, new MantleFloatingObjectComponent(engineMantle).getRadius());
    }
}
