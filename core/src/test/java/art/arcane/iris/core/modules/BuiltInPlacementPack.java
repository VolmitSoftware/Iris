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
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import com.volmit.IrisModule;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test packs whose placement graph is Iris's built-in modules followed by the given pack modules. */
public final class BuiltInPlacementPack {
    private BuiltInPlacementPack() {
    }

    /** Loads the pack and lets the mocked {@code data} serve it, as a pack's data serves its modules. Close it after the test. */
    public static LoadedPack serve(IrisData data, TemporaryFolder folder, List<IrisModule> packModules) throws IOException {
        List<IrisModule> modules = Stream.concat(IrisModuleHost.builtInModules().stream(), packModules.stream()).toList();
        LoadedPack pack = LoadedPack.load(folder.newFolder().toPath(), ModuleHost.of(modules).copyingJarsTo(folder.newFolder().toPath()));
        PackModuleSet moduleSet = mock(PackModuleSet.class);
        when(moduleSet.pack()).thenReturn(pack);
        when(data.getModules()).thenReturn(moduleSet);
        when(data.getDataFolder()).thenReturn(folder.getRoot());
        return pack;
    }

    /** Placements into a world of {@code engine} derive their seeds from its world seed. */
    public static void seedWorld(Engine engine, long seed) {
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getSeed()).thenReturn(seed);
        when(engine.getSeedManager()).thenReturn(seeds);
    }
}
