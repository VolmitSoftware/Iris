package com.volmit.host;

import com.volmit.host.schema.PackSchema;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** One loaded pack: its module jars and everything derived from them together with the host's
 * built-in modules. This is the host's entry point for loading and unloading a pack's modules.
 *
 * <pre>{@code
 * try (var pack = LoadedPack.load(packRoot, host)) {
 *     var placer = new PlacementRunner(pack.placement());
 *     ...
 * }
 * }</pre>
 *
 * A load either returns a complete pack or throws with nothing left open. Close the pack when it
 * unloads, after the host has stopped using its graphs, catalog and runners: closing releases the
 * pack's class loader and deletes its copies of the module jars. The original jars are never held
 * open, so a module author can rebuild them while the pack is loaded.
 */
public final class LoadedPack implements AutoCloseable {
    private final PackModules packModules;
    private final LoadedModules loaded;

    private LoadedPack(PackModules packModules, LoadedModules loaded) {
        this.packModules = packModules;
        this.loaded = loaded;
    }

    /** Loads the pack's module jars, installs them with the host's built-in modules and resolves both
     * pipelines. Fails with the first load error, naming the jar or module at fault.
     */
    public static LoadedPack load(Path packRoot, ModuleHost host) {
        Objects.requireNonNull(host, "host");
        var packModules = PackModules.load(packRoot, host.exposedHostPackages(), host.jarCopyRoot(), host.logger());
        try {
            var loaded = LoadedModules.load(host.withPackModules(packModules.modules()),
                    host.reservedHostFields(), host.reservedNamespaces());
            return new LoadedPack(packModules, loaded);
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(packModules, failure);
            throw failure;
        }
    }

    /** The pack's own modules, one per jar, in jar file name order. */
    public List<DiscoveredModule> packModules() {
        return packModules.modules();
    }

    /** Classes the pack's jars use that nobody has, per jar; see {@link PackModules#missingClasses()}. */
    public List<MissingClasses> missingClasses() {
        return packModules.missingClasses();
    }

    public FeatureCatalog catalog() {
        return loaded.catalog();
    }

    /** What the pack's modules, built-in ones included, add to the pack's JSON, for studio's schema
     * and completion. Built with the pack, so a load that returns has a complete description.
     */
    public PackSchema schema() {
        return loaded.schema();
    }

    public ResolvedGraph placement() {
        return loaded.placement();
    }

    public ResolvedGraph chunk() {
        return loaded.chunk();
    }

    @Override
    public void close() {
        packModules.close();
    }

    private static void closeAfterFailure(PackModules packModules, Throwable failure) {
        try {
            packModules.close();
        } catch (RuntimeException exception) {
            failure.addSuppressed(exception);
        }
    }
}
