package com.volmit.host;

import com.volmit.resource.ResourceId;
import java.util.Optional;

/** A pack whose modules cannot load: a jar that breaks the pack rules, a module whose setup fails, or modules that do not
 * fit together. Every load error of {@link LoadedPack#load}, {@link PackModules#load} and {@link LoadedModules#load} is
 * one, so a host catches this type alone. The message is complete on its own and names the jar or module at fault.
 */
public final class ModuleLoadException extends RuntimeException {
    private final ResourceId moduleId;
    private final String origin;

    /** {@code moduleId} and {@code origin} (the jar, such as {@code modules/moss.jar}, or {@code host classpath}) are null
     * when the error is not one module's or one jar's, such as a dependency cycle between modules; {@code cause} is null
     * when there is none.
     */
    public ModuleLoadException(ResourceId moduleId, String origin, String message, Throwable cause) {
        super(message, cause);
        this.moduleId = moduleId;
        this.origin = origin;
    }

    /** The module at fault, when the error is one module's. */
    public Optional<ResourceId> moduleId() {
        return Optional.ofNullable(moduleId);
    }

    /** Where the module or jar at fault comes from, when the error is one module's or one jar's. */
    public Optional<String> origin() {
        return Optional.ofNullable(origin);
    }

    /** A jar's error, before any of its modules exists. */
    static ModuleLoadException ofJar(String origin, String message) {
        return new ModuleLoadException(null, origin, message, null);
    }

    /** An error that is not one module's or one jar's. */
    static ModuleLoadException ofPack(String message) {
        return new ModuleLoadException(null, null, message, null);
    }

    /** {@code failure} as a load error of {@code module}, with its message unchanged; a load error already is one. */
    static ModuleLoadException of(DiscoveredModule module, RuntimeException failure) {
        if (failure instanceof ModuleLoadException loadError) return loadError;
        return new ModuleLoadException(module.info().id(), module.origin(), failure.getMessage(), failure);
    }

    /** {@code failure} as a load error that is not one module's or one jar's, with its message unchanged; a load error
     * already is one.
     */
    static ModuleLoadException of(RuntimeException failure) {
        if (failure instanceof ModuleLoadException loadError) return loadError;
        return new ModuleLoadException(null, null, failure.getMessage(), failure);
    }
}
