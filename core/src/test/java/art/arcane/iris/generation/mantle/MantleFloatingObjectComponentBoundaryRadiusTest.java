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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.biome.IrisFloatingChildBiomes;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectTranslate;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MantleFloatingObjectComponentBoundaryRadiusTest {
    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

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
        when(dimension.getAllObjectScaleFactor()).thenReturn(1D);
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
        when(dimension.getAllObjectScaleFactor()).thenReturn(2D);
        assertEquals(34, new MantleFloatingObjectComponent(engineMantle).getRadius());
    }
}
