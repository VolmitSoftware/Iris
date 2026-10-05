package com.volmit.host;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import java.util.Objects;

/** A module, its identity and where the host found it. Load errors name the origin so the author
 * knows which jar to fix.
 *
 * <p>{@link IrisModule#info()} is read once, when the module is discovered, and only that value is
 * used afterwards. A module whose {@code info()} changes between calls cannot give the host
 * inconsistent answers.
 */
public final class DiscoveredModule {
    static final String HOST_CLASSPATH = "host classpath";

    /** Where a module comes from: the host's classpath or a pack jar. */
    enum Source { HOST, PACK }

    private final IrisModule module;
    private final ModuleInfo info;
    private final String origin;
    private final Source source;

    private DiscoveredModule(IrisModule module, ModuleInfo info, String origin, Source source) {
        this.module = Objects.requireNonNull(module, "module");
        this.info = Objects.requireNonNull(info, "info");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.source = Objects.requireNonNull(source, "source");
        if (origin.isBlank()) {
            throw new IllegalArgumentException("Blank module origin: " + module.getClass().getName());
        }
    }

    /** A module that ships with the host instead of a pack. Reads its {@code info()}; a failure
     * propagates unchanged.
     */
    public static DiscoveredModule builtIn(IrisModule module) {
        Objects.requireNonNull(module, "module");
        return new DiscoveredModule(module, readInfo(module), HOST_CLASSPATH, Source.HOST);
    }

    /** A pack module whose {@code info()} the jar loader has already read and checked. */
    static DiscoveredModule fromJar(IrisModule module, ModuleInfo info, String origin) {
        return new DiscoveredModule(module, info, origin, Source.PACK);
    }

    private static ModuleInfo readInfo(IrisModule module) {
        return Objects.requireNonNull(module.info(), () -> "info() of " + module.getClass().getName() + " returned null");
    }

    public IrisModule module() {
        return module;
    }

    /** The module's identity, as {@code info()} returned it at discovery. */
    public ModuleInfo info() {
        return info;
    }

    /** The jar path relative to the pack, or "host classpath" for built-in modules. */
    public String origin() {
        return origin;
    }

    Source source() {
        return source;
    }

    @Override
    public String toString() {
        return info.id() + " from " + origin;
    }
}
