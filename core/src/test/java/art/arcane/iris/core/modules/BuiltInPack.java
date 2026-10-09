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
import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test packs of Iris's built-in modules followed by the given pack modules, loaded as Iris's module host loads them. */
public final class BuiltInPack {
    private BuiltInPack() {
    }

    /** A new empty pack folder, to put module jars into (see {@link PackModuleJar}) before {@link #serve(IrisData, TemporaryFolder, Path, List)}. */
    public static Path newPackFolder(TemporaryFolder folder) throws IOException {
        return folder.newFolder().toPath();
    }

    /** Loads the pack and lets the mocked {@code data} serve it, as a pack's data serves its modules. Close it after the test. */
    public static LoadedPack serve(IrisData data, TemporaryFolder folder, List<IrisModule> packModules) throws IOException {
        return serve(data, folder, newPackFolder(folder), packModules);
    }

    /**
     * Like {@link #serve(IrisData, TemporaryFolder, List)} for a pack folder whose {@code modules} folder may hold module jars:
     * they load through the pack loader after the compiled-in built-ins and {@code packModules}, as in a real pack. The jars see the
     * same packages a real pack's modules see.
     */
    public static LoadedPack serve(IrisData data, TemporaryFolder folder, Path packFolder, List<IrisModule> packModules) throws IOException {
        List<IrisModule> modules = Stream.concat(IrisModuleHost.builtInModules().stream(), packModules.stream()).toList();
        LoadedPack pack = LoadedPack.load(packFolder, ModuleHost.of(modules)
                .exposing(IrisModuleHost.BUILT_IN_API_PACKAGE)
                .reservingFields(ModuleTargets.reservedFields())
                .describingHostTypesIn(IrisModuleHost.HOST_TYPE_PACKAGE)
                .copyingJarsTo(folder.newFolder().toPath()));
        PackModuleSet moduleSet = mock(PackModuleSet.class);
        when(moduleSet.pack()).thenReturn(pack);
        when(data.getModules()).thenReturn(moduleSet);
        when(data.getDataFolder()).thenReturn(folder.getRoot());
        return pack;
    }

    /** {@link #serve} and the pack's resources ({@link PackResourceFixture#serve}), which a world's vanilla structure hooks read. */
    public static LoadedPack serveWithResources(IrisData data, TemporaryFolder folder, List<IrisModule> packModules) throws IOException {
        LoadedPack pack = serve(data, folder, packModules);
        PackResourceFixture.serve(data);
        return pack;
    }

    /** {@code values} as the module settings a dimension, region or biome declares, as the decoder would have read them. */
    public static ModuleSettings settings(IrisFeature... values) {
        Map<Class<? extends IrisFeature>, IrisFeature> byType = new HashMap<>();
        for (IrisFeature value : values) {
            byType.put(value.getClass(), value);
        }
        return ModuleSettings.of(byType);
    }

    /** Placements into a world of {@code engine} derive their seeds from its world seed. */
    public static void seedWorld(Engine engine, long seed) {
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getSeed()).thenReturn(seed);
        when(engine.getSeedManager()).thenReturn(seeds);
    }
}
