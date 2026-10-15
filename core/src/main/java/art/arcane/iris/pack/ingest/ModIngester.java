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

package art.arcane.iris.pack.ingest;

import art.arcane.iris.pack.AtomicDirectoryPublisher;
import art.arcane.iris.pack.ingest.catalog.CatalogBiome;
import art.arcane.iris.pack.ingest.catalog.CatalogNamespace;
import art.arcane.iris.pack.ingest.catalog.CatalogPlacedFeature;
import art.arcane.iris.pack.ingest.catalog.CatalogStructure;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;
import art.arcane.iris.structure.nativegen.IrisImportedFeatureControl;
import art.arcane.iris.studio.workspace.IrisProjectCopier;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class ModIngester {
    private static final Set<String> RESERVED_NAMESPACES = Set.of("minecraft", "c", "iris");

    private ModIngester() {
    }

    public static ModIngestReport ingest(ModIngestRequest request) throws IOException {
        Objects.requireNonNull(request, "request");
        Path source = request.sourcePack().toAbsolutePath().normalize();
        Path target = request.targetPack().toAbsolutePath().normalize();
        requireSeparate(source, target);
        if (Files.exists(source.resolve(IngestManifest.PATH), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Source pack " + source + " was written by mod ingest; ingest from its original pack instead.");
        }
        Set<String> namespaces = selectNamespaces(request);
        prepareTarget(target, request.replaceTarget());
        String targetKey = target.getFileName().toString();
        IrisProjectCopier.copyProject(source.toFile(), target.getParent().toFile(), request.dimensionKey(), targetKey);
        try {
            return populate(request, target, targetKey, namespaces);
        } catch (IOException | RuntimeException e) {
            try {
                AtomicDirectoryPublisher.deleteTree(target);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    private static ModIngestReport populate(ModIngestRequest request, Path target, String targetKey, Set<String> namespaces) throws IOException {
        WorldgenCatalogIndex index = WorldgenCatalogIndex.of(request.catalog());
        IngestPack pack = new IngestPack(target);
        IngestPackModel model = IngestPackModel.load(pack, targetKey, index);
        ModFeatureIngester features = new ModFeatureIngester(model, index, namespaces);
        List<ModIngestItem> biomeItems = new ArrayList<>();
        Map<String, ModBiomeAdopter.AdoptedBiome> adopted = new ModBiomeAdopter(model, index, features.decoratorKeys())
                .adopt(modBiomes(index, namespaces), biomeItems);
        List<ModIngestItem> featureItems = features.apply(adopted);
        JsonObject dimension = pack.edit(model.dimensionPath());
        IrisImportedFeatureControl control = ImportedFeatureWriter.write(dimension, features.nativeKeys());
        featureItems = ImportedFeatureWriter.applySuppression(featureItems, features.plans(), control);
        Set<String> hostBiomes = new TreeSet<>(adopted.keySet());
        hostBiomes.addAll(model.reachableBiomes().values());
        List<ModIngestItem> items = new ArrayList<>(biomeItems);
        items.addAll(featureItems);
        items.addAll(ModStructureIngester.ingest(request.catalog().structures(), namespaces, hostBiomes, dimension));
        pack.flush();
        IngestManifest.write(pack, request.catalog(), request.dimensionKey(), targetKey, namespaces, items);
        return new ModIngestReport(target, items, IngestManifest.summary(targetKey, pack, namespaces, items));
    }

    private static Set<String> selectNamespaces(ModIngestRequest request) {
        Set<String> selected = new TreeSet<>();
        if (!request.namespaces().isEmpty()) {
            for (String namespace : request.namespaces()) {
                String normalized = namespace.trim().toLowerCase(Locale.ROOT);
                if (RESERVED_NAMESPACES.contains(normalized)) {
                    throw new IllegalArgumentException("Namespace " + normalized + " cannot be ingested; it belongs to Minecraft or Iris.");
                }
                selected.add(normalized);
            }
            return selected;
        }
        WorldgenCatalog catalog = request.catalog();
        for (CatalogNamespace namespace : catalog.namespaces()) {
            selected.add(namespace.namespace());
        }
        for (CatalogBiome biome : catalog.biomes()) {
            selected.add(WorldgenCatalogIndex.namespaceOf(biome.key()));
        }
        for (CatalogPlacedFeature feature : catalog.placedFeatures()) {
            selected.add(WorldgenCatalogIndex.namespaceOf(feature.key()));
        }
        for (CatalogStructure structure : catalog.structures()) {
            selected.add(WorldgenCatalogIndex.namespaceOf(structure.key()));
        }
        selected.removeAll(RESERVED_NAMESPACES);
        return selected;
    }

    private static List<CatalogBiome> modBiomes(WorldgenCatalogIndex index, Set<String> namespaces) {
        List<CatalogBiome> biomes = new ArrayList<>();
        for (String namespace : namespaces) {
            biomes.addAll(index.biomesIn(namespace));
        }
        biomes.sort(Comparator.comparing(CatalogBiome::key));
        return biomes;
    }

    private static void requireSeparate(Path source, Path target) throws IOException {
        Path realSource = Files.exists(source) ? source.toRealPath() : source;
        if (target.getParent() == null) {
            throw new IOException("Target pack needs a parent directory: " + target);
        }
        for (Path candidate : List.of(source, realSource)) {
            if (candidate.equals(target) || target.startsWith(candidate) || candidate.startsWith(target)) {
                throw new IOException("Target pack " + target + " overlaps the source pack " + candidate);
            }
        }
    }

    private static void prepareTarget(Path target, boolean replace) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            if (!replace) {
                throw new FileAlreadyExistsException(target.toString());
            }
            AtomicDirectoryPublisher.deleteTree(target);
        }
        Files.createDirectories(target.getParent());
    }
}
