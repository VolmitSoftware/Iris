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

import art.arcane.iris.engine.framework.structure.StructureLocating;
import art.arcane.iris.engine.loot.LootGiving;
import art.arcane.iris.engine.mantle.components.StructureModule;
import com.volmit.IrisModule;
import com.volmit.host.FeatureCatalog;
import com.volmit.host.LifecycleRegistration;
import com.volmit.service.BlockDrops;
import com.volmit.service.Loot;
import com.volmit.service.ObjectsAt;
import com.volmit.service.StructureLocator;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The services and hooks Iris's built-in modules provide, which Iris core asks instead of their features. */
public class BuiltInServicesTest {
    private static final FeatureCatalog BUILT_INS = ModuleResourceFixture.catalogOf(IrisModuleHost.builtInModules().toArray(IrisModule[]::new));

    @Test
    public void objectsTellWhichObjectsAChunkGets() {
        assertEquals(1, BUILT_INS.services(ObjectsAt.class).size());
    }

    @Test
    public void lootAndBlockDropsComeFromTheLootModule() {
        assertEquals(1, BUILT_INS.services(Loot.class).size());
        assertEquals(1, BUILT_INS.services(BlockDrops.class).size());
        assertTrue(BUILT_INS.services(Loot.class).getFirst() instanceof LootGiving);
    }

    @Test
    public void structuresAreFoundThroughTheStructureModule() {
        List<StructureLocator> locators = BUILT_INS.services(StructureLocator.class);
        assertEquals(1, locators.size());
        assertTrue(locators.getFirst() instanceof StructureLocating);
    }

    @Test
    public void theModulesThatNeedToHearAboutWorldsHearThem() {
        assertEquals(List.of("iris:decorant", "iris:objects", "iris:structure", "iris:terrain"), BUILT_INS.lifecycles().stream()
                .map(LifecycleRegistration::module).map(module -> module.info().id().toString()).toList());
        assertTrue(BUILT_INS.lifecycles().stream().anyMatch(registration -> registration.lifecycle() instanceof StructureModule));
    }
}
