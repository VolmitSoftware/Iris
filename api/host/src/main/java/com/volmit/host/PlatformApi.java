package com.volmit.host;

import java.util.Objects;

/** The platform a host runs on: its {@link ToolingPlatform description} and the loader that defines its api classes, such
 * as the Iris plugin's. The host checks the parts of that platform against the loader's classes, by class file so that
 * nothing is loaded, and a {@link ToolingPartLoader} delegates the platform's api packages to it.
 */
public record PlatformApi(ToolingPlatform platform, ClassLoader classes) {
    private static final String CLASS_FILE_SUFFIX = ".class";

    public PlatformApi {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(classes, "classes");
    }

    boolean hasClass(String binaryName) {
        return classes.getResource(binaryName.replace('.', '/') + CLASS_FILE_SUFFIX) != null;
    }
}
