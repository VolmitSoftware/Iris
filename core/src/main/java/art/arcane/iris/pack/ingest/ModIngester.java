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
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.pack.ingest.catalog.CatalogBiome;
import art.arcane.iris.pack.ingest.catalog.CatalogNamespace;
import art.arcane.iris.pack.ingest.catalog.CatalogPlacedFeature;
import art.arcane.iris.pack.ingest.catalog.CatalogStructure;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;
import art.arcane.iris.structure.nativegen.IrisImportedFeatureControl;
import art.arcane.iris.studio.workspace.IrisProjectCopier;
import com.google.gson.JsonObject;

import java.io.File;
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
import java.util.UUID;
import java.util.stream.Stream;

public final class ModIngester {
    private static final Set<String> RESERVED_NAMESPACES = Set.of("minecraft", "c", "iris", "irisworldgen");
    private static final String JSON = ".json";

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
        Set<String> namespaces = selectNamespaces(request, reservedNamespaces(request.catalog(), source, target.getParent()));
        requireReplaceableTarget(target, request.replaceTarget());
        String targetKey = target.getFileName().toString();
        Files.createDirectories(target.getParent());
        Path workspace = Files.createTempDirectory(target.getParent(), "." + targetKey + ".ingest-");
        try {
            IrisProjectCopier.copyProject(source.toFile(), workspace.toFile(), request.dimensionKey(), targetKey);
            ModIngestReport built = populate(request, workspace.resolve(targetKey), targetKey, namespaces);
            publish(workspace.resolve(targetKey), target, request.replaceTarget());
            return new ModIngestReport(target, built.items(), built.summaryLines());
        } finally {
            AtomicDirectoryPublisher.deleteTree(workspace);
        }
    }

    private static ModIngestReport populate(ModIngestRequest request, Path target, String targetKey, Set<String> namespaces) throws IOException {
        WorldgenCatalogIndex index = WorldgenCatalogIndex.of(request.catalog());
        IngestPack pack = new IngestPack(target);
        IngestPackModel model = IngestPackModel.load(pack, targetKey, index);
        ModFeatureIngester features = new ModFeatureIngester(model, index, namespaces);
        List<ModIngestItem> biomeItems = new ArrayList<>();
        requireKnownRegions(model, request.excludedRegions());
        Map<String, ModBiomeAdopter.AdoptedBiome> adopted = new ModBiomeAdopter(model, index, features.decoratorKeys(),
                request.excludedRegions()).adopt(modBiomes(index, namespaces), biomeItems);
        List<ModIngestItem> featureItems = features.apply(adopted);
        JsonObject dimension = pack.edit(model.dimensionPath());
        IrisImportedFeatureControl control = ImportedFeatureWriter.write(dimension, features.nativeKeys(),
                unselectedFeatureNamespaces(request.catalog(), namespaces), features.vanillaScopedKeys());
        featureItems = ImportedFeatureWriter.applySuppression(featureItems, features.plans(), control);
        Set<String> hostBiomes = new TreeSet<>(adopted.keySet());
        hostBiomes.addAll(model.reachableBiomes().values());
        List<ModIngestItem> items = new ArrayList<>(biomeItems);
        items.addAll(featureItems);
        items.addAll(ModStructureIngester.ingest(request.catalog().structures(), namespaces, hostBiomes, dimension));
        pack.flush();
        Set<String> reported = reportedNamespaces(namespaces, items);
        IngestManifest.write(pack, request.catalog(), request.dimensionKey(), targetKey, reported, items);
        return new ModIngestReport(target, items, IngestManifest.summary(targetKey, pack, reported, items));
    }

    private static void requireKnownRegions(IngestPackModel model, Set<String> excludedRegions) {
        Set<String> unknown = new TreeSet<>(excludedRegions);
        for (IngestPackModel.RegionProfile region : model.regions()) {
            unknown.remove(region.key());
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Excluded regions " + unknown + " are not regions of dimension " + model.dimensionPath() + ".");
        }
    }

    private static Set<String> reportedNamespaces(Set<String> namespaces, List<ModIngestItem> items) {
        Set<String> reported = new TreeSet<>();
        for (ModIngestItem item : items) {
            if (namespaces.contains(item.namespace())) {
                reported.add(item.namespace());
            }
        }
        return reported;
    }

    private static Set<String> selectNamespaces(ModIngestRequest request, Set<String> reserved) {
        Set<String> selected = new TreeSet<>();
        if (!request.namespaces().isEmpty()) {
            for (String namespace : request.namespaces()) {
                String normalized = namespace.trim().toLowerCase(Locale.ROOT);
                if (reserved.contains(normalized)) {
                    throw new IllegalArgumentException("Namespace " + normalized + " cannot be ingested; it belongs to Minecraft, Iris, or an installed Iris pack dimension.");
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
        selected.removeAll(reserved);
        return selected;
    }

    private static Set<String> reservedNamespaces(WorldgenCatalog catalog, Path source, Path packsRoot) throws IOException {
        Set<String> dimensionKeys = new TreeSet<>(dimensionKeys(source));
        for (File pack : PackDirectoryResolver.listVisiblePackDirectories(packsRoot.toFile())) {
            dimensionKeys.addAll(dimensionKeys(pack.toPath()));
        }
        for (CatalogNamespace namespace : catalog.namespaces()) {
            dimensionKeys.remove(namespace.namespace());
        }
        Set<String> reserved = new TreeSet<>(RESERVED_NAMESPACES);
        reserved.addAll(dimensionKeys);
        return reserved;
    }

    private static Set<String> dimensionKeys(Path pack) throws IOException {
        Path dimensions = pack.resolve("dimensions");
        if (!Files.isDirectory(dimensions, LinkOption.NOFOLLOW_LINKS)) {
            return Set.of();
        }
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.list(dimensions)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(JSON) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    keys.add(name.substring(0, name.length() - JSON.length()).toLowerCase(Locale.ROOT));
                }
            }
        }
        return keys;
    }

    private static Set<String> unselectedFeatureNamespaces(WorldgenCatalog catalog, Set<String> selected) {
        Set<String> unselected = new TreeSet<>();
        for (CatalogPlacedFeature feature : catalog.placedFeatures()) {
            unselected.add(WorldgenCatalogIndex.namespaceOf(feature.key()));
        }
        unselected.removeAll(selected);
        unselected.remove(ImportedFeatureWriter.VANILLA_PREFIX.substring(0, ImportedFeatureWriter.VANILLA_PREFIX.length() - 1));
        return unselected;
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
        if (target.getParent() == null) {
            throw new IOException("Target pack needs a parent directory: " + target);
        }
        Path realSource = Files.exists(source) ? source.toRealPath() : source;
        List<Path> targets = new ArrayList<>(List.of(target));
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            targets.add(target.toRealPath());
            if (Files.exists(source) && Files.isSameFile(source, target)) {
                throw new IOException("Target pack " + target + " is the source pack " + realSource);
            }
        } else if (Files.exists(target.getParent())) {
            targets.add(target.getParent().toRealPath().resolve(target.getFileName()));
        }
        for (Path candidate : List.of(source, realSource)) {
            for (Path resolvedTarget : targets) {
                if (candidate.equals(resolvedTarget) || resolvedTarget.startsWith(candidate) || candidate.startsWith(resolvedTarget)) {
                    throw new IOException("Target pack " + target + " overlaps the source pack " + candidate);
                }
            }
        }
    }

    private static void requireReplaceableTarget(Path target, boolean replace) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(target)) {
            return;
        }
        if (!replace) {
            throw new FileAlreadyExistsException(target.toString());
        }
        if (Files.isSymbolicLink(target)
                || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(target.resolve(IngestManifest.PATH), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Iris only replaces packs written by mod ingest, and " + target + " has no "
                    + IngestManifest.PATH + "; choose another target pack name.");
        }
    }

    private static void publish(Path built, Path target, boolean replace) throws IOException {
        Path staged = target.resolveSibling("." + target.getFileName() + ".staged-" + UUID.randomUUID());
        Files.move(built, staged);
        try (AtomicDirectoryPublisher.Publication publication = replace
                ? AtomicDirectoryPublisher.publish(staged, target)
                : AtomicDirectoryPublisher.publishAbsent(staged, target)) {
            publication.commit();
            publication.cleanupBackup();
        } finally {
            AtomicDirectoryPublisher.deleteTree(staged);
        }
    }
}
