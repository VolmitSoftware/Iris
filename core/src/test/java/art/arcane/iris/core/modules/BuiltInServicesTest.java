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
import art.arcane.iris.builtin.loot.LootResolver;
import art.arcane.iris.engine.framework.structure.StructureLocating;
import art.arcane.iris.engine.loot.LootGiving;
import art.arcane.iris.engine.mantle.components.ObjectLocating;
import art.arcane.iris.engine.mantle.components.StructureModule;
import art.arcane.iris.engine.mantle.components.StructurePiecesAt;
import com.volmit.IrisModule;
import com.volmit.geometry.BlockPosition;
import com.volmit.host.FeatureCatalog;
import com.volmit.host.LifecycleRegistration;
import com.volmit.service.BlockDrops;
import com.volmit.service.Loot;
import com.volmit.service.ObjectsAt;
import com.volmit.service.ProtectedFloor;
import com.volmit.service.StructureLocator;
import com.volmit.world.PackWorld;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The services and hooks Iris's built-in modules provide, which Iris core asks instead of their features. */
public class BuiltInServicesTest {
    private static final FeatureCatalog BUILT_INS = ModuleResourceFixture.catalogOf(BuiltInPack.allBuiltIns().toArray(IrisModule[]::new));

    @Test
    public void objectsAndStructurePiecesTellWhatTheyPlace() {
        List<ObjectsAt> providers = BUILT_INS.services(ObjectsAt.class);
        assertEquals(2, providers.size());
        assertTrue(providers.stream().anyMatch(ObjectLocating.class::isInstance));
        assertTrue(providers.stream().anyMatch(StructurePiecesAt.class::isInstance));
    }

    @Test
    public void lootAndBlockDropsComeFromTheLootModule() {
        assertEquals(1, BUILT_INS.services(Loot.class).size());
        assertEquals(1, BUILT_INS.services(BlockDrops.class).size());
        assertTrue(BUILT_INS.services(Loot.class).getFirst() instanceof LootGiving);
    }

    @Test
    public void theLootModuleShufflesAContainerWithTheSeedOfItsSpotAtWorldHeight() {
        PackWorld world = mock(PackWorld.class);
        when(world.seed(IrisSeedPurposes.LOOT)).thenReturn(99L);
        when(world.minWorldY()).thenReturn(-64);
        Loot.Site site = new Loot.Site(new BlockPosition(5, 70, -9), Optional.empty(), false, false);

        assertEquals(LootResolver.containerSeed(99L, 5, 6, -9), new LootGiving().shuffleSeed(world, site).getAsLong());
    }

    @Test
    public void theTerrainModuleProtectsTheFloor() {
        assertEquals(1, BUILT_INS.services(ProtectedFloor.class).size());
    }

    @Test
    public void structuresAreFoundThroughTheStructureModule() {
        List<StructureLocator> locators = BUILT_INS.services(StructureLocator.class);
        assertEquals(1, locators.size());
        assertTrue(locators.getFirst() instanceof StructureLocating);
    }

    @Test
    public void theModulesThatNeedToHearAboutWorldsHearThem() {
        assertEquals(List.of("iris:decorant", "iris:objects", "iris:terrain"), BUILT_INS.lifecycles().stream()
                .map(LifecycleRegistration::module).map(module -> module.info().id().toString()).toList());
    }
}
