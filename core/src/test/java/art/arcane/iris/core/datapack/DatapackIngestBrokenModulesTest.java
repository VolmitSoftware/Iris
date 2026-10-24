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

package art.arcane.iris.core.datapack;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.PackModuleSet;
import art.arcane.iris.core.modules.PackModulesException;
import com.volmit.host.ModuleLoadException;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** At startup datapack ingest, a pack whose modules fail to load is skipped with an error, and the other packs are asked as before. */
public class DatapackIngestBrokenModulesTest {
    @Test
    @SuppressWarnings("unchecked")
    public void aPackWhoseModulesFailToLoadIsSkipped() {
        ResourceLoader<IrisDimension> dimensions = mock(ResourceLoader.class);
        when(dimensions.getPossibleKeys()).thenReturn(new String[]{"overworld"});
        when(dimensions.loadAll(new String[]{"overworld"})).thenReturn(new KList<>(new IrisDimension()));
        PackModuleSet modules = mock(PackModuleSet.class);
        when(modules.pack()).thenThrow(new PackModulesException(new File("broken"), new ModuleLoadException(null, null, "Duplicate module example:moss", null)));
        IrisData data = mock(IrisData.class);
        when(data.getDimensionLoader()).thenReturn(dimensions);
        when(data.getModules()).thenReturn(modules);
        when(data.getDataFolder()).thenReturn(new File("broken"));

        assertFalse(DatapackIngestService.packDisablesOverrides(data));
        verify(modules).pack();
    }
}
