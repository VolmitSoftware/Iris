package com.volmit.support;

import com.volmit.host.PlatformApi;
import com.volmit.host.ToolingPlatform;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/** A tiny stand-in for a platform's api jar, such as paper-api: a jar of empty public classes, and a loader that defines
 * only them, like the platform plugin's loader would. Close it to release the jar.
 */
public final class FakePlatformApi implements AutoCloseable {
    private final Path jar;
    private final URLClassLoader classes;

    private FakePlatformApi(Path jar, URLClassLoader classes) {
        this.jar = jar;
        this.classes = classes;
    }

    /** Writes a jar below {@code workDirectory} that holds one empty public class per name, such as {@code org.bukkit.Bukkit}. */
    public static FakePlatformApi of(Path workDirectory, String... classNames) {
        try {
            var jarSource = new TestModuleJar();
            for (var className : classNames) {
                int dot = className.lastIndexOf('.');
                jarSource.source(className, "package " + className.substring(0, dot) + "; public class " + className.substring(dot + 1) + " {}");
            }
            var jar = jarSource.writeInto(Files.createTempDirectory(workDirectory, "platform"), "platform-api.jar");
            return new FakePlatformApi(jar, new URLClassLoader("fake-platform", new URL[] {jar.toUri().toURL()}, null));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public Path jar() {
        return jar;
    }

    public ClassLoader classes() {
        return classes;
    }

    public PlatformApi asApiOf(ToolingPlatform platform) {
        return new PlatformApi(platform, classes);
    }

    @Override
    public void close() {
        try {
            classes.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
