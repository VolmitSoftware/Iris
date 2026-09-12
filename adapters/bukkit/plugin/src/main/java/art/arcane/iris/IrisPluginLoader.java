package art.arcane.iris;

import art.arcane.iris.platform.bootstrap.SlimJar;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@SuppressWarnings("UnstableApiUsage")
public final class IrisPluginLoader implements PluginLoader {
    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        PluginProviderContext context = classpathBuilder.getContext();
        Path libraryRoot = context.getDataDirectory().resolve("cache").resolve("libraries");
        SlimJar.loadBootstrap(libraryRoot, new SlimJar.BootstrapLogger() {
            @Override
            public void info(String message) {
                context.getLogger().info(message);
            }

            @Override
            public void error(String message) {
                context.getLogger().error(message);
            }

            @Override
            public void debug(String message) {
                context.getLogger().debug(message);
            }
        });
        for (Path library : relocatedLibraries(libraryRoot)) {
            classpathBuilder.addLibrary(new JarLibrary(library));
        }
    }

    static List<Path> relocatedLibraries(Path libraryRoot) {
        try (Stream<Path> paths = Files.walk(libraryRoot)) {
            List<Path> libraries = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(IrisPluginLoader::isRelocatedLibrary)
                    .sorted()
                    .toList();
            if (libraries.isEmpty()) {
                throw new IllegalStateException("Iris runtime library provisioning produced no relocated libraries.");
            }
            return libraries;
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to enumerate provisioned Iris runtime libraries.", failure);
        }
    }

    private static boolean isRelocatedLibrary(Path path) {
        int count = path.getNameCount();
        for (int i = 0; i < count - 2; i++) {
            if ("relocated".equals(path.getName(i).toString())
                    && "Iris".equals(path.getName(i + 1).toString())) {
                return true;
            }
        }
        return false;
    }
}
