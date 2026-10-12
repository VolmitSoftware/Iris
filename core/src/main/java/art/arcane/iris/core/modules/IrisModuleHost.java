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
import art.arcane.iris.engine.actuator.TerrainModule;
import art.arcane.iris.engine.loot.LootModule;
import art.arcane.iris.engine.mantle.components.ObjectModule;
import art.arcane.iris.engine.spawn.SpawnModule;
import art.arcane.iris.engine.mantle.components.StructureModule;
import art.arcane.iris.engine.modifier.CarveModule;
import art.arcane.iris.engine.modifier.FloatingModule;
import art.arcane.iris.engine.object.placement.PlacementModules;
import art.arcane.iris.spi.IrisPlatforms;
import com.volmit.IrisModule;
import com.volmit.ModuleLog;
import com.volmit.host.ModuleHost;
import com.volmit.host.PackModules;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The one {@link ModuleHost} every pack load uses: Iris's built-in modules, the packages exposed to packs (the
 * built-in module API and the libraries Iris shares), Iris's own JSON fields, Iris's pack object
 * model as host types, the pack folders Iris reads itself, a jar copy directory under the plugin's cache, and
 * Iris's log.
 * <p>
 * The built-in modules that are already ported ship in the built-ins jar ({@code IrisModule/builtin-modules.txt}), which a
 * pack puts into its {@code modules} folder like any pack module; they are not listed here.
 */
public final class IrisModuleHost {
    /** Exposed to pack modules, with its subpackages; see its package documentation. */
    public static final String BUILT_IN_API_PACKAGE = "art.arcane.iris.builtin";
    /**
     * Libraries pack modules may call besides the IrisApi, each with its subpackages: VolmLib, which Iris shades unrelocated,
     * and Gson, which every platform Iris runs on provides.
     */
    public static final List<String> SHARED_LIBRARY_PACKAGES = List.of("art.arcane.volmlib", "com.google.gson");
    /** Every package pack modules may use besides the IrisApi. {@code exposedHostPackages} in IrisModule's build lists the same. */
    public static final List<String> EXPOSED_PACKAGES = Stream.concat(Stream.of(BUILT_IN_API_PACKAGE), SHARED_LIBRARY_PACKAGES.stream()).toList();
    /**
     * The ids of the built-in modules that ship in the built-ins jar ({@code iris-builtins.jar}, listed in
     * {@code IrisModule/builtin-modules.txt}) instead of being compiled into Iris. A pack loads them from its {@code modules}
     * folder, and {@link MissingBuiltInModules} warns when it loads none; a test keeps this equal to the jar's list.
     */
    public static final List<String> JAR_MODULE_IDS = List.of("iris:post", "iris:perfection", "iris:matter", "iris:deposit", "iris:decorant");
    /** Iris's pack object model: built-in settings may hold these classes, which Iris reads and describes itself. */
    public static final String HOST_TYPE_PACKAGE = "art.arcane.iris.engine.object";
    private static final String[] JAR_COPY_FOLDER = {"cache", "module-jars"};
    private static final String TEMPORARY_DIRECTORY_PROPERTY = "java.io.tmpdir";

    private IrisModuleHost() {
    }

    public static ModuleHost get() {
        return Holder.HOST;
    }

    /** The modules every pack loads before its own: Iris's own, placement and the chunk generation every world runs. */
    public static List<IrisModule> builtInModules() {
        return Stream.of(hostModules(), PlacementModules.all(), generationModules()).flatMap(List::stream).toList();
    }

    /** Modules that are Iris itself, not features: they write the layout's platform biomes and record the platform's custom blocks. A pack never replaces them and the built-ins jar does not hold them. */
    private static List<IrisModule> hostModules() {
        return List.of(new BiomeModule(), new CustomModule());
    }

    private static List<IrisModule> generationModules() {
        return List.of(new TerrainModule(), new CarveModule(), new ObjectModule(), new StructureModule(),
                new FloatingModule(), new LootModule(), new SpawnModule());
    }

    /** Iris's own log, labelled {@code [iris]}, for host code that calls module classes outside any module's stage, service or hook. */
    public static ModuleLog hostLog() {
        return IrisApiLogger.hostLog();
    }

    /** Whether {@code type} is one of Iris's own classes that built-in settings may hold. */
    public static boolean isHostType(Class<?> type) {
        return type.getName().startsWith(HOST_TYPE_PACKAGE + ".");
    }

    private static ModuleHost build() {
        return ModuleHost.of(builtInModules())
                .exposing(EXPOSED_PACKAGES.toArray(String[]::new))
                .reservingFields(ModuleTargets.reservedFields())
                .describingHostTypesIn(HOST_TYPE_PACKAGE)
                .reservingFolders(IrisData.ownFolderNames().toArray(String[]::new))
                .copyingJarsTo(jarCopyRoot())
                .loggingTo(new IrisApiLogger(PackModules.class.getName()));
    }

    /** Under the plugin's cache folder when a platform is bound; tests and the standalone probe use the JDK's temporary directory. */
    private static Path jarCopyRoot() {
        if (!IrisPlatforms.isBound()) {
            return Path.of(System.getProperty(TEMPORARY_DIRECTORY_PROPERTY));
        }
        return IrisPlatforms.get().dataFolderNoCreate(JAR_COPY_FOLDER).toPath();
    }

    private static final class Holder {
        private static final ModuleHost HOST = build();
    }
}
