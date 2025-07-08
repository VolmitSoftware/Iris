package com.volmit.iris;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import lombok.SneakyThrows;
import org.bukkit.configuration.file.YamlConfiguration;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;

@SuppressWarnings("all")
public class IrisPluginLoader implements PluginLoader {

    @SneakyThrows
    @Override
    public void classloader(@NotNull PluginClasspathBuilder builder) {
        var pluginUri = URI.create("jar:file:" + builder.getContext().getPluginSource().toAbsolutePath() + "!/plugin.yml");
        var plugin = YamlConfiguration.loadConfiguration(new BufferedReader(new InputStreamReader(pluginUri.toURL().openStream())));
        var repository = "https://maven-central.storage-download.googleapis.com/maven2";

        try {
            var field = MavenLibraryResolver.class.getDeclaredField("MAVEN_CENTRAL_DEFAULT_MIRROR");
            repository = (String) field.get(null);
        } catch (Throwable e) {}

        var resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("central", "default", repository).build());
        plugin.getStringList("libraries").forEach(library -> resolver.addDependency(new Dependency(new DefaultArtifact(library), null)));
        builder.addLibrary(resolver);
    }
}
