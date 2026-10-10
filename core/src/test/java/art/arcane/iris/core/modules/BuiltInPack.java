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
import art.arcane.iris.engine.modifier.MatterModule;
import art.arcane.iris.engine.modifier.PerfectionModule;
import art.arcane.iris.engine.modifier.PostModule;
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

    /**
     * Compiled-in copies of the built-in modules that ship in the built-ins jar ({@code IrisModule/builtin-modules.txt}), so tests
     * that need Iris's whole generation graph get it without building the jar. Until the cut-over the classes are compiled
     * with Iris; {@code BuiltInModulesJarTest} loads the same modules from the jar instead.
     */
    public static List<IrisModule> jarModules() {
        return List.of(new PostModule(), new PerfectionModule(), new MatterModule());
    }

    /** Every built-in module: Iris's own and the compiled-in copies of the jar's ({@link #jarModules()}). */
    public static List<IrisModule> allBuiltIns() {
        return Stream.concat(IrisModuleHost.builtInModules().stream(), jarModules().stream()).toList();
    }

    /** A new empty pack folder, to put module jars into (see {@link PackModuleJar}) before {@link #serve(IrisData, TemporaryFolder, Path, List)}. */
    public static Path newPackFolder(TemporaryFolder folder) throws IOException {
        return folder.newFolder().toPath();
    }

    /**
     * Loads every built-in ({@link #allBuiltIns()}) and {@code packModules} and lets the mocked {@code data} serve the pack, as a
     * pack's data serves its modules. Close it after the test.
     */
    public static LoadedPack serve(IrisData data, TemporaryFolder folder, List<IrisModule> packModules) throws IOException {
        return serve(data, folder, newPackFolder(folder), Stream.concat(jarModules().stream(), packModules.stream()).toList());
    }

    /**
     * Like {@link #serve(IrisData, TemporaryFolder, List)} for a pack folder whose {@code modules} folder may hold module jars:
     * they load through the pack loader after Iris's compiled-in built-ins and {@code packModules}, as in a real pack. The jars see
     * the same packages a real pack's modules see. The jar's built-ins are not added here: put them into {@code packModules}
     * ({@link #jarModules()}) or the built-ins jar into the pack.
     */
    public static LoadedPack serve(IrisData data, TemporaryFolder folder, Path packFolder, List<IrisModule> packModules) throws IOException {
        List<IrisModule> modules = Stream.concat(IrisModuleHost.builtInModules().stream(), packModules.stream()).toList();
        LoadedPack pack = LoadedPack.load(packFolder, ModuleHost.of(modules)
                .exposing(IrisModuleHost.EXPOSED_PACKAGES.toArray(String[]::new))
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
