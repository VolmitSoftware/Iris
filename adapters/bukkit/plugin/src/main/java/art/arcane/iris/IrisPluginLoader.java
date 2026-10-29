package art.arcane.iris;

import art.arcane.iris.platform.bootstrap.SlimJar;
import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@SuppressWarnings("UnstableApiUsage")
public final class IrisPluginLoader implements PluginLoader {
    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        PluginProviderContext context = classpathBuilder.getContext();
        Path libraryRoot = context.getDataDirectory().resolve("cache").resolve("libraries");
        SlimJar.loadBootstrap(libraryRoot, ServerBuildInfo.buildInfo().minecraftVersionId(), new SlimJar.BootstrapLogger() {
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
            if (!library.startsWith(libraryRoot.resolve("com/github/VolmitSoftware/VolmLib"))) {
                classpathBuilder.addLibrary(new JarLibrary(library));
            }
        }
        classpathBuilder.addLibrary(mavenResolver(mavenLibraries(context.getPluginSource())));
    }

    static List<String> mavenLibraries(Path pluginJar) {
        try (ZipFile jar = new ZipFile(pluginJar.toFile())) {
            ZipEntry descriptor = jar.getEntry("plugin.yml");
            if (descriptor == null) {
                throw new IllegalStateException("The Iris jar " + pluginJar + " has no plugin.yml library list.");
            }
            try (Reader reader = new InputStreamReader(jar.getInputStream(descriptor), StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader).getStringList("libraries");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to read the Iris library list from " + pluginJar + ".", failure);
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

    private static MavenLibraryResolver mavenResolver(List<String> coordinates) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("central", "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        for (String coordinate : coordinates) {
            resolver.addDependency(new Dependency(new DefaultArtifact(coordinate), null));
        }
        return resolver;
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
