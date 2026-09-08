/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.datapack;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.volmlib.util.collection.KList;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

final class DatapackImportSources {
    static final String LOCAL_IMPORT_DIRECTORY = "imports";

    private DatapackImportSources() {
    }

    static Set<String> mergeConfiguredImports(
            Iterable<String> explicit,
            Iterable<String> discovered
    ) {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        if (explicit != null) {
            addImports(explicit, sources);
        }
        if (discovered != null) {
            addImports(discovered, sources);
        }
        return sources.isEmpty() ? Set.of() : Set.copyOf(sources);
    }

    static List<String> discoverLocalDatapackImports(File directory) throws IOException {
        Path root = directory.toPath().toAbsolutePath().normalize();
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(root);
        }
        if (Files.isSymbolicLink(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Local datapack import path is not a safe directory: " + root);
        }
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(DatapackImportSources::isLocalDatapackArchive)
                    .sorted(Comparator.comparing(
                                    (Path path) -> path.getFileName().toString(),
                                    String.CASE_INSENSITIVE_ORDER)
                            .thenComparing((Path path) -> path.getFileName().toString()))
                    .map(path -> path.toAbsolutePath().normalize().toUri().toASCIIString())
                    .toList();
        }
    }

    static boolean isLocalDatapackArchive(Path path) {
        String name = path.getFileName().toString();
        return !name.startsWith(".") && name.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    static List<String> localDatapackImports() {
        File directory = IrisPlatforms.get().dataFolder("datapacks", LOCAL_IMPORT_DIRECTORY);
        try {
            return discoverLocalDatapackImports(directory);
        } catch (IOException exception) {
            IrisLogging.reportError("Could not discover local datapack imports under "
                    + directory.getPath() + ".", exception);
            return List.of();
        }
    }

    static void collectImports(IrisData data, LinkedHashSet<String> urls) {
        if (data == null || data.getDimensionLoader() == null) {
            return;
        }
        for (IrisDimension dimension : data.getDimensionLoader().loadAll(data.getDimensionLoader().getPossibleKeys())) {
            if (dimension == null) {
                continue;
            }
            KList<String> imports = dimension.getDatapackImports();
            if (imports == null) {
                continue;
            }
            addImports(imports, urls);
        }
    }

    static void addImports(Iterable<String> imports, LinkedHashSet<String> sources) {
        for (String source : imports) {
            if (source != null && !source.isBlank()) {
                sources.add(normalizeConfiguredSource(source));
            }
        }
    }

    static String normalizeConfiguredSource(String source) {
        String normalized = source.trim();
        try {
            URI uri = new URI(normalized);
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return Path.of(uri).toAbsolutePath().normalize().toUri().toASCIIString();
            }
        } catch (IllegalArgumentException | URISyntaxException ignored) {
        }
        return normalized;
    }

    static Set<String> configuredImports(IrisData data) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        collectImports(data, urls);
        urls.addAll(localDatapackImports());
        return urls;
    }

    static boolean hasImports(IrisData data) {
        return !configuredImports(data).isEmpty();
    }
}
