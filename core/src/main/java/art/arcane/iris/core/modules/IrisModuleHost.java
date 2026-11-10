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
import art.arcane.iris.spi.IrisPlatforms;
import com.volmit.IrisModule;
import com.volmit.ModuleLog;
import com.volmit.host.ModuleHost;
import com.volmit.host.PackModules;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * The one {@link ModuleHost} every pack load uses: Iris's built-in modules, the packages exposed to packs (the
 * built-in module API and the libraries Iris shares), Iris's own JSON fields, Iris's pack object
 * model as host types, the pack folders Iris reads itself, a jar copy directory under the plugin's cache, and
 * Iris's log.
 * <p>
 * The built-in modules that are already ported ship in the built-ins jar ({@code IrisModule/builtin-modules.txt}), which the
 * plugin bundles and every pack loads as if it were in its {@code modules} folder; they are not listed here.
 */
public final class IrisModuleHost {
    /** Exposed to pack modules, with its subpackages; see its package documentation. */
    public static final String BUILT_IN_API_PACKAGE = "art.arcane.iris.builtin";
    /**
     * Libraries pack modules may call besides the IrisApi, each with its subpackages: VolmLib, which Iris shades unrelocated,
     * and Gson, which every platform Iris runs on provides.
     */
    public static final List<String> SHARED_LIBRARY_PACKAGES = List.of("art.arcane.volmlib", "com.google.gson");
    /**
     * The structure file transactions Iris's datapack ingest and the jigsaw module's tools both run, which each would
     * otherwise see as a copy of its own; exposed with its subpackages.
     */
    public static final String STRUCTURE_FILES_PACKAGE = "art.arcane.iris.core.structure.authoring";
    /** Every package pack modules may use besides the IrisApi. {@code exposedHostPackages} in IrisModule's build lists the same. */
    public static final List<String> EXPOSED_PACKAGES = Stream.concat(Stream.of(BUILT_IN_API_PACKAGE, STRUCTURE_FILES_PACKAGE),
            SHARED_LIBRARY_PACKAGES.stream()).toList();
    /**
     * The ids of the built-in modules that ship in the built-ins jar ({@code iris-builtins.jar}, listed in
     * {@code IrisModule/builtin-modules.txt}) instead of being compiled into Iris. Every pack loads them from the jar Iris
     * bundles, and {@link MissingBuiltInModules} warns when it loads none; a test keeps this equal to the jar's list.
     */
    public static final List<String> JAR_MODULE_IDS = List.of("iris:post", "iris:perfection", "iris:matter", "iris:loot",
            "iris:spawn", "iris:prepare", "iris:anchor", "iris:validate", "iris:bore", "iris:blocks", "iris:stilt", "iris:vacuum",
            "iris:snow", "iris:vanilla-features", "iris:carve", "iris:deposit", "iris:decorant", "iris:terrain", "iris:objects",
            "iris:floating", "iris:structure", "iris:jigsaw");
    /**
     * Iris's pack object model: built-in settings may hold these classes, which Iris reads and describes itself. The
     * structure model (engine.object.structure) is not among them: it speaks the api's vocabulary and its module describes it.
     */
    public static final String[] HOST_TYPE_PACKAGES = Stream.of("annotations", "compatibility", "image", "material", "noise",
                    "placement", "prefab", "runtime", "terrain", "value", "world")
            .map(subpackage -> "art.arcane.iris.engine.object." + subpackage)
            .toArray(String[]::new);
    private static final String[] JAR_COPY_FOLDER = {"cache", "module-jars"};
    private static final String BUNDLED_JAR_RESOURCE = "/" + MissingBuiltInModules.JAR_NAME;
    private static final String BUNDLED_JAR_TEMPORARY_PREFIX = "iris-built-in-modules-";
    private static final String TEMPORARY_DIRECTORY_PROPERTY = "java.io.tmpdir";

    private IrisModuleHost() {
    }

    public static ModuleHost get() {
        return Holder.HOST;
    }

    /** The modules every pack loads before its own: Iris's own. */
    public static List<IrisModule> builtInModules() {
        return hostModules();
    }

    /**
     * Modules Iris registers itself, not from the built-ins jar: those that are Iris (they write the layout's platform
     * biomes and record the platform's custom blocks). A pack never replaces them.
     */
    private static List<IrisModule> hostModules() {
        return List.of(new BiomeModule(), new CustomModule());
    }

    /** Iris's own log, labelled {@code [iris]}, for host code that calls module classes outside any module's stage, service or hook. */
    public static ModuleLog hostLog() {
        return IrisApiLogger.hostLog();
    }

    /** Whether {@code type} is one of Iris's own classes that built-in settings may hold. */
    public static boolean isHostType(Class<?> type) {
        return Stream.of(HOST_TYPE_PACKAGES).anyMatch(hostPackage -> type.getName().startsWith(hostPackage + "."));
    }

    private static ModuleHost build() {
        return ModuleHost.of(builtInModules())
                .exposing(EXPOSED_PACKAGES.toArray(String[]::new))
                .reservingFields(ModuleTargets.reservedFields())
                .describingHostTypesIn(HOST_TYPE_PACKAGES)
                .reservingFolders(IrisData.ownFolderNames().toArray(String[]::new))
                .bundlingJars(bundledJars())
                .copyingJarsTo(jarCopyRoot())
                .loggingTo(new IrisApiLogger(PackModules.class.getName()));
    }

    /**
     * The built-ins jar this build of Iris carries, unpacked to a file the pack loader can copy; none when the classpath
     * holds no such jar, as in Iris's own tests, which put a copy into the pack instead.
     */
    private static Path[] bundledJars() {
        try (InputStream bundled = IrisModuleHost.class.getResourceAsStream(BUNDLED_JAR_RESOURCE)) {
            if (bundled == null) {
                return new Path[0];
            }
            Path unpacked = bundledJarFolder().resolve(MissingBuiltInModules.JAR_NAME);
            Files.copy(bundled, unpacked, StandardCopyOption.REPLACE_EXISTING);
            return new Path[]{unpacked};
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot unpack Iris's built-in modules jar", exception);
        }
    }

    /** A fresh temporary folder, removed when the JVM exits; the host is built before a platform may be bound. */
    private static Path bundledJarFolder() throws IOException {
        Path folder = Files.createTempDirectory(BUNDLED_JAR_TEMPORARY_PREFIX);
        folder.toFile().deleteOnExit();
        folder.resolve(MissingBuiltInModules.JAR_NAME).toFile().deleteOnExit();
        return folder;
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
