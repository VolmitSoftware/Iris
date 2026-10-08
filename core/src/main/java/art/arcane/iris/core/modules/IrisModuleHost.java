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

import art.arcane.iris.engine.modifier.PerfectionModule;
import art.arcane.iris.engine.object.placement.PlacementModules;
import art.arcane.iris.spi.IrisPlatforms;
import com.volmit.IrisModule;
import com.volmit.host.ModuleHost;
import com.volmit.host.PackModules;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The one {@link ModuleHost} every pack load uses: Iris's built-in modules, the built-in module API
 * package exposed to packs, Iris's own JSON fields, the reserved {@code iris} namespace, Iris's pack object
 * model as host types, a jar copy directory under the plugin's cache, and Iris's log.
 */
public final class IrisModuleHost {
    /** Exposed to pack modules, with its subpackages; see its package documentation. */
    public static final String BUILT_IN_API_PACKAGE = "art.arcane.iris.builtin";
    /** Iris's pack object model: built-in settings may hold these classes, which Iris reads and describes itself. */
    public static final String HOST_TYPE_PACKAGE = "art.arcane.iris.engine.object";
    static final String RESERVED_NAMESPACE = "iris";
    private static final String[] JAR_COPY_FOLDER = {"cache", "module-jars"};
    private static final String TEMPORARY_DIRECTORY_PROPERTY = "java.io.tmpdir";

    private IrisModuleHost() {
    }

    public static ModuleHost get() {
        return Holder.HOST;
    }

    /** The modules every pack loads before its own: Iris's built-in features. */
    public static List<IrisModule> builtInModules() {
        return Stream.concat(PlacementModules.all().stream(), Stream.of(new PerfectionModule())).toList();
    }

    /** Whether {@code type} is one of Iris's own classes that built-in settings may hold. */
    public static boolean isHostType(Class<?> type) {
        return type.getName().startsWith(HOST_TYPE_PACKAGE + ".");
    }

    private static ModuleHost build() {
        return ModuleHost.of(builtInModules())
                .exposing(BUILT_IN_API_PACKAGE)
                .reservingNamespaces(RESERVED_NAMESPACE)
                .reservingFields(ModuleTargets.reservedFields())
                .describingHostTypesIn(HOST_TYPE_PACKAGE)
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
