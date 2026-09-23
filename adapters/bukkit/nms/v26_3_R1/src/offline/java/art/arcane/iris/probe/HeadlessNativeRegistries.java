package art.arcane.iris.probe;

import art.arcane.iris.pack.datapack.IrisDatapackCompiler;
import art.arcane.iris.pack.datapack.v263.DataFixerV263;
import art.arcane.volmlib.util.collection.KList;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

final class HeadlessNativeRegistries implements AutoCloseable {
    private final Path directory;
    private final MultiPackResourceManager resources;
    private final RegistryAccess.Frozen registries;

    private HeadlessNativeRegistries(Path directory, MultiPackResourceManager resources, RegistryAccess.Frozen registries) {
        this.directory = directory;
        this.resources = resources;
        this.registries = registries;
    }

    static HeadlessNativeRegistries load(Map<String, String> definitions) throws IOException {
        HeadlessNativeBootstrap.initialize();
        Path directory = Files.createTempDirectory("iris-native-registry-");
        try {
            for (Map.Entry<String, String> definition : definitions.entrySet()) {
                Path file = directory.resolve(definition.getKey()).normalize();
                if (!file.startsWith(directory.resolve("data")) || !file.toString().endsWith(".json")) {
                    throw new IllegalArgumentException("Invalid generated registry resource " + definition.getKey());
                }
                Files.createDirectories(file.getParent());
                Files.writeString(file, definition.getValue());
            }
            return load(directory);
        } catch (IOException | RuntimeException failure) {
            delete(directory);
            throw failure;
        }
    }

    static HeadlessNativeRegistries compile(File pack) throws IOException {
        HeadlessNativeBootstrap.initialize();
        Path directory = Files.createTempDirectory("iris-native-pack-");
        try {
            IrisDatapackCompiler.compile(List.of(pack), new KList<>(directory.toFile()), List.of(),
                    new DataFixerV263(), false);
            return load(directory);
        } catch (IOException | RuntimeException failure) {
            delete(directory);
            throw failure;
        }
    }

    private static HeadlessNativeRegistries load(Path directory) {
        PackLocationInfo location = new PackLocationInfo("iris-headless", Component.literal("Iris"),
                PackSource.WORLD, Optional.empty());
        MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(
                ServerPacksSource.createVanillaPackSource().fullResources(), new PathPackResources(location, directory)));
        try {
            RegistryAccess.Frozen builtins = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            RegistryAccess.Frozen loaded;
            try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
                loaded = RegistryDataLoader.load(resources, builtins.listRegistries().toList(),
                        RegistryDataLoader.WORLD_REGISTRIES, executor).join();
            }
            RegistryAccess.Frozen registries = new RegistryAccess.ImmutableRegistryAccess(
                    Stream.concat(builtins.registries(), loaded.registries())).freeze();
            HeadlessNativeBootstrap.bindComponents(registries);
            return new HeadlessNativeRegistries(directory, resources, registries);
        } catch (RuntimeException failure) {
            resources.close();
            throw failure;
        }
    }

    RegistryAccess.Frozen registries() {
        return registries;
    }

    MultiPackResourceManager resources() {
        return resources;
    }

    @Override
    public void close() throws IOException {
        resources.close();
        delete(directory);
    }

    private static void delete(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
