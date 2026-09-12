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

package art.arcane.iris.pack.datapack;

import art.arcane.iris.pack.datapack.DatapackIngestService.Entry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.BulkStructureImporter;
import art.arcane.iris.structure.StructureImporter;
import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureSource;
import art.arcane.iris.structure.authoring.StructureTransactionWriter;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.stream.Stream;

final class DatapackStructureImports {
    static final int STRUCTURE_IMPORT_FORMAT_REVISION = 3;

    private DatapackStructureImports() {
    }

    static boolean autoImportDatapackStructures() {
        DatapackTransactions.TRANSACTION_LOCK.lock();
        try {
            return autoImportDatapackStructuresLocked();
        } finally {
            DatapackTransactions.TRANSACTION_LOCK.unlock();
        }
    }

    static boolean autoImportDatapackStructuresLocked() {
        boolean autoImportEnabled = IrisSettings.get().getGeneral().autoImportDatapackStructures;
        File root = IrisPlatforms.get().dataFolder("datapacks");
        boolean recovered;
        try {
            recovered = DatapackScratchRecovery.recoverTransactions(root, ServerConfigurator.getDatapacksFolder());
        } catch (IOException e) {
            IrisLogging.reportError("Automatic datapack structure import blocked by incomplete transaction recovery.", e);
            return false;
        }
        Manifest manifest = DatapackManifestStore.readManifest(root);
        if (manifest.entries.isEmpty()) {
            return recovered;
        }
        Map<String, Entry> manifestEntriesByUrl = new HashMap<>();
        for (Entry entry : manifest.entries) {
            if (entry.url != null) {
                manifestEntriesByUrl.put(entry.url, entry);
            }
        }

        List<IrisData> packs;
        try (Stream<IrisData> stream = ServerConfigurator.allPacks()) {
            packs = stream.filter(Objects::nonNull).toList();
        }
        if (!autoImportEnabled && !hasRemovedImportState(packs, manifest.entries)) {
            return recovered;
        }

        Map<String, Entry> entriesByUrl = new HashMap<>();
        File stagingRoot = new File(root, "staging");
        boolean stagingStateChanged = false;
        boolean manifestStagingMetadataChanged = false;
        for (Entry entry : manifest.entries) {
            if (entry.url == null) {
                continue;
            }
            StagingInspection inspection = DatapackStagingGuard.inspectUsableStaging(new File(stagingRoot, entry.id), entry);
            stagingStateChanged |= inspection.ownershipCorrected();
            manifestStagingMetadataChanged |= inspection.manifestChanged();
            if (inspection.usable()) {
                entriesByUrl.put(entry.url, entry);
            }
        }

        int attemptedPacks = 0;
        int completedPacks = 0;
        int cleanupTargets = 0;
        Set<String> completedUrls = new HashSet<>();
        Set<String> failedUrls = new HashSet<>();
        for (IrisData data : packs) {
            Set<String> configured = DatapackImportSources.configuredImports(data);
            String targetId = data.getDataFolder().toPath().toAbsolutePath().normalize().toString();
            for (Entry entry : manifest.entries) {
                if (!configured.contains(entry.url) && hasImportState(entry, targetId)) {
                    cleanupTargets++;
                }
            }
            if (!cleanupRemovedImports(data, targetId, configured, manifest.entries, manifestEntriesByUrl)) {
                for (Entry entry : manifest.entries) {
                    if (!configured.contains(entry.url) && hasImportState(entry, targetId)) {
                        failedUrls.add(entry.url);
                    }
                }
            }
            if (!autoImportEnabled) {
                continue;
            }

            Set<String> pendingUrls = new HashSet<>();
            for (String url : configured) {
                Entry entry = entriesByUrl.get(url);
                if (entry != null && importPending(entry, targetId)) {
                    pendingUrls.add(url);
                }
            }
            if (pendingUrls.isEmpty()) {
                continue;
            }
            for (String pendingUrl : pendingUrls) {
                Entry entry = entriesByUrl.get(pendingUrl);
                prepareImportRecoveryInventory(entry, targetId);
            }
            try {
                DatapackManifestStore.writeManifestChecked(root, manifest);
            } catch (IOException e) {
                failedUrls.addAll(pendingUrls);
                IrisLogging.reportError("Datapack structure import for pack '"
                        + data.getDataFolder().getPath()
                        + "' was blocked because its recovery inventory could not be persisted.", e);
                continue;
            }
            Set<String> structureKeys = new TreeSet<>();
            Set<String> templateKeys = new TreeSet<>();
            for (Entry entry : manifest.entries) {
                if (!configured.contains(entry.url)) {
                    continue;
                }
                structureKeys.addAll(DatapackManifestStore.copyList(entry.structureKeys));
                templateKeys.addAll(DatapackManifestStore.copyList(entry.templateKeys));
            }
            attemptedPacks++;
            try {
                BulkStructureImporter.Report report = BulkStructureImporter.importManagedDatapackStructures(
                        data,
                        StructureImporter.Mode.OVERWRITE,
                        BukkitPlatform.console(),
                        structureKeys,
                        templateKeys
                );
                Set<String> successfulPendingUrls = new HashSet<>(pendingUrls);
                Set<String> incompleteUrls = new HashSet<>();
                for (String pendingUrl : pendingUrls) {
                    Entry entry = entriesByUrl.get(pendingUrl);
                    Map<String, String> desired = importBundleInventory(entry);
                    if (!report.successfulBundles().entrySet().containsAll(desired.entrySet())) {
                        successfulPendingUrls.remove(pendingUrl);
                        incompleteUrls.add(pendingUrl);
                        failedUrls.add(pendingUrl);
                    }
                }
                if (report.failed() > 0 && incompleteUrls.isEmpty()) {
                    successfulPendingUrls.clear();
                    incompleteUrls.addAll(pendingUrls);
                    failedUrls.addAll(pendingUrls);
                }
                if (!incompleteUrls.isEmpty()) {
                    boolean reconciled = reconcileFailedImportInventories(
                            root, manifest, data, targetId, incompleteUrls, entriesByUrl);
                    if (report.retryRequired() || !reconciled) {
                        IrisLogging.error("Datapack structure import for pack '%s' reported %d incomplete source(s) and remains pending because a retryable runtime failure occurred.",
                                data.getDataFolder().getPath(), incompleteUrls.size());
                    } else if (recordDeterministicImportAttempts(
                            root, manifest, targetId, incompleteUrls, entriesByUrl)) {
                        IrisLogging.warn("Datapack structure import for pack '"
                                + data.getDataFolder().getPath() + "' left " + incompleteUrls.size()
                                + " source(s) incomplete after deterministic validation failures. Iris will retain the partial editable imports without retrying until the datapack source, importer format, or target pack changes.");
                    }
                }
                Map<String, String> sharedBundles = desiredBundles(configured, entriesByUrl);
                boolean packCompleted = false;
                for (String pendingUrl : successfulPendingUrls) {
                    Entry entry = entriesByUrl.get(pendingUrl);
                    Map<String, String> desired = importBundleInventory(entry);
                    Map<String, String> previous = entry.importedBundles.getOrDefault(targetId, Map.of());
                    Map<String, String> stale = new TreeMap<>(previous);
                    stale.keySet().removeAll(desired.keySet());
                    stale.keySet().removeAll(sharedBundles.keySet());
                    Map<String, String> remaining = cleanupImportedBundles(data, stale);
                    if (!remaining.isEmpty()) {
                        failedUrls.add(pendingUrl);
                        continue;
                    }
                    entry.importedBundles.put(targetId, desired);
                    recordSuccessfulImport(entry, targetId);
                    completedUrls.add(pendingUrl);
                    packCompleted = true;
                }
                if (packCompleted) {
                    completedPacks++;
                }
            } catch (RuntimeException e) {
                failedUrls.addAll(pendingUrls);
                IrisLogging.reportError("Datapack structure import failed for pack '"
                        + data.getDataFolder().getPath() + "'; the manifest remains pending for retry.", e);
                reconcileFailedImportInventories(root, manifest, data, targetId, pendingUrls, entriesByUrl);
            }
        }

        for (Entry entry : manifest.entries) {
            if (failedUrls.contains(entry.url)) {
                entry.structuresImported = false;
            } else if (completedUrls.contains(entry.url)) {
                entry.structuresImported = true;
            }
        }
        if (attemptedPacks == 0 && cleanupTargets == 0) {
            if (manifestStagingMetadataChanged) {
                DatapackManifestStore.writeManifest(root, manifest);
            }
            return recovered || stagingStateChanged || manifestStagingMetadataChanged;
        }
        DatapackManifestStore.writeManifest(root, manifest);
        if (attemptedPacks == 0) {
            IrisLogging.info("Datapack editable-import cleanup reconciled " + cleanupTargets + " removed source target(s).");
            return true;
        }
        IrisLogging.info("Datapack structure import refreshed " + completedUrls.size() + " source(s) across "
                + completedPacks + "/" + attemptedPacks
                + " pack(s). Reference the imported keys from a 'structures' placement to position them manually.");
        return true;
    }

    static boolean hasRemovedImportState(List<IrisData> packs, List<Entry> entries) {
        for (IrisData data : packs) {
            Set<String> configured = DatapackImportSources.configuredImports(data);
            String targetId = data.getDataFolder().toPath().toAbsolutePath().normalize().toString();
            for (Entry entry : entries) {
                if (!configured.contains(entry.url) && hasImportState(entry, targetId)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String importRevision(Entry entry) {
        return importRevision(entry, STRUCTURE_IMPORT_FORMAT_REVISION);
    }

    static String importRevision(Entry entry, int importerFormatRevision) {
        return "v" + importerFormatRevision + ":" + DatapackSupport.safe(entry.versionId) + ":" + DatapackSupport.safe(entry.sha1);
    }

    static boolean importPending(Entry entry, String targetId) {
        String revision = importRevision(entry);
        return !revision.equals(entry.importedTargets.get(targetId))
                && !revision.equals(entry.importAttempts.get(targetId));
    }

    static void recordDeterministicImportAttempt(Entry entry, String targetId) {
        entry.importedTargets.remove(targetId);
        entry.importAttempts.put(targetId, importRevision(entry));
        entry.structuresImported = false;
    }

    static void recordSuccessfulImport(Entry entry, String targetId) {
        entry.importedTargets.put(targetId, importRevision(entry));
        entry.importAttempts.remove(targetId);
    }

    static void prepareImportRecoveryInventory(Entry entry, String targetId) {
        Map<String, String> desired = importBundleInventory(entry);
        Map<String, String> recovery = new TreeMap<>(desired);
        recovery.putAll(entry.importedBundles.getOrDefault(targetId, Map.of()));
        entry.importedBundles.put(targetId, recovery);
        entry.importedTargets.remove(targetId);
        entry.importAttempts.remove(targetId);
        entry.structuresImported = false;
    }

    static boolean reconcileFailedImportInventories(
            File root,
            Manifest manifest,
            IrisData data,
            String targetId,
            Set<String> pendingUrls,
            Map<String, Entry> entriesByUrl
    ) {
        boolean reconciled = true;
        for (String pendingUrl : pendingUrls) {
            Entry entry = entriesByUrl.get(pendingUrl);
            try {
                reconcileFailedImportInventory(data, entry, targetId);
            } catch (IOException | RuntimeException e) {
                reconciled = false;
                IrisLogging.reportError("Could not reconcile partial editable structure imports for '"
                        + pendingUrl + "' in pack '" + data.getDataFolder().getPath()
                        + "'; the conservative recovery inventory remains pending.", e);
            }
        }
        try {
            DatapackManifestStore.writeManifestChecked(root, manifest);
        } catch (IOException e) {
            reconciled = false;
            IrisLogging.reportError("Could not persist reconciled partial editable structure imports for pack '"
                    + data.getDataFolder().getPath() + "'; the earlier recovery inventory remains durable.", e);
        }
        return reconciled;
    }

    static boolean recordDeterministicImportAttempts(
            File root,
            Manifest manifest,
            String targetId,
            Set<String> incompleteUrls,
            Map<String, Entry> entriesByUrl
    ) {
        for (String incompleteUrl : incompleteUrls) {
            recordDeterministicImportAttempt(entriesByUrl.get(incompleteUrl), targetId);
        }
        try {
            DatapackManifestStore.writeManifestChecked(root, manifest);
            return true;
        } catch (IOException e) {
            for (String incompleteUrl : incompleteUrls) {
                entriesByUrl.get(incompleteUrl).importAttempts.remove(targetId);
            }
            IrisLogging.reportError("Could not persist deterministic editable structure import attempts; the sources remain pending for retry.", e);
            return false;
        }
    }

    static void reconcileFailedImportInventory(
            IrisData data,
            Entry entry,
            String targetId
    ) throws IOException {
        Map<String, Set<String>> claims = DatapackRemoval.importBundleClaims(entry, targetId);
        Map<String, String> reconciled = new TreeMap<>();
        StructureTransactionWriter writer = new StructureTransactionWriter(data.getDataFolder().toPath());
        for (Map.Entry<String, Set<String>> bundle : claims.entrySet()) {
            StructureKey targetKey;
            try {
                targetKey = StructureKey.parse(bundle.getKey());
            } catch (RuntimeException e) {
                throw new IOException("Invalid editable structure target key '" + bundle.getKey() + "'", e);
            }
            Optional<StructureSource> source = writer.ownedSource(targetKey);
            if (source.isPresent() && DatapackRemoval.sourceClaimsContain(bundle.getValue(), source.get())) {
                reconciled.put(bundle.getKey(), source.get().key().value());
            }
        }
        if (reconciled.isEmpty()) {
            entry.importedBundles.remove(targetId);
        } else {
            entry.importedBundles.put(targetId, reconciled);
        }
        entry.importedTargets.remove(targetId);
        entry.importAttempts.remove(targetId);
        entry.structuresImported = false;
    }

    static boolean cleanupRemovedImports(
            IrisData data,
            String targetId,
            Set<String> configured,
            List<Entry> entries,
            Map<String, Entry> entriesByUrl
    ) {
        boolean successful = true;
        Map<String, String> retainedBundles = desiredBundles(configured, entriesByUrl);
        for (Entry entry : entries) {
            if (configured.contains(entry.url)) {
                continue;
            }
            Map<String, String> inventory = entry.importedBundles.get(targetId);
            if (inventory == null && !hasImportState(entry, targetId)) {
                continue;
            }
            Map<String, String> resolvedInventory = inventory == null ? Map.of() : inventory;
            for (Entry retainedEntry : entries) {
                if (configured.contains(retainedEntry.url)) {
                    retainedEntry.importedTargets.remove(targetId);
                    retainedEntry.importAttempts.remove(targetId);
                    retainedEntry.structuresImported = false;
                }
            }
            Map<String, String> removable = new TreeMap<>(resolvedInventory);
            removable.keySet().removeAll(retainedBundles.keySet());
            Map<String, String> remaining = cleanupImportedBundles(data, removable);
            if (!remaining.isEmpty()) {
                Map<String, String> retained = new TreeMap<>();
                for (Map.Entry<String, String> bundle : resolvedInventory.entrySet()) {
                    if (retainedBundles.containsKey(bundle.getKey()) || remaining.containsKey(bundle.getKey())) {
                        retained.put(bundle.getKey(), bundle.getValue());
                    }
                }
                entry.importedBundles.put(targetId, retained);
                entry.importedTargets.remove(targetId);
                entry.importAttempts.remove(targetId);
                entry.structuresImported = false;
                successful = false;
                continue;
            }
            entry.importedBundles.remove(targetId);
            entry.importedTargets.remove(targetId);
            entry.importAttempts.remove(targetId);
        }
        return successful;
    }

    static boolean hasImportState(Entry entry, String targetId) {
        return entry.importedBundles.containsKey(targetId)
                || entry.importedTargets.containsKey(targetId)
                || entry.importAttempts.containsKey(targetId);
    }

    static Map<String, String> cleanupImportedBundles(IrisData data, Map<String, String> inventory) {
        Map<String, String> remaining = new TreeMap<>();
        StructureTransactionWriter writer = new StructureTransactionWriter(data.getDataFolder().toPath());
        boolean removed = false;
        for (Map.Entry<String, String> bundle : inventory.entrySet()) {
            StructureKey sourceKey;
            try {
                sourceKey = StructureKey.parse(bundle.getValue());
                StructureSource.Kind sourceKind = sourceKey.namespace().equals("minecraft")
                        ? StructureSource.Kind.VANILLA : StructureSource.Kind.DATAPACK;
                removed |= writer.removeManagedDatapackOwned(
                        StructureKey.parse(bundle.getKey()),
                        sourceKind,
                        sourceKey
                );
            } catch (IOException | RuntimeException e) {
                remaining.put(bundle.getKey(), bundle.getValue());
                IrisLogging.reportError("Preserving imported structure bundle '" + bundle.getKey()
                        + "' in pack '" + data.getDataFolder().getPath()
                        + "' because ownership-safe cleanup failed.", e);
            }
        }
        if (removed) {
            data.invalidateStructureResources();
        }
        return remaining;
    }

    static Map<String, String> desiredBundles(Set<String> configured, Map<String, Entry> entriesByUrl) {
        Map<String, String> bundles = new TreeMap<>();
        for (String url : configured) {
            Entry entry = entriesByUrl.get(url);
            if (entry != null) {
                bundles.putAll(importBundleInventory(entry));
            }
        }
        return bundles;
    }

    static Map<String, String> importBundleInventory(Entry entry) {
        Map<String, String> bundles = new TreeMap<>();
        for (String structureKey : DatapackManifestStore.copyList(entry.structureKeys)) {
            bundles.put("iris:" + StructureImporter.deriveName(structureKey), structureKey);
        }
        for (String templateKey : DatapackManifestStore.copyList(entry.templateKeys)) {
            bundles.put("iris:" + BulkStructureImporter.templateNameFor(templateKey), templateKey);
        }
        return bundles;
    }
}
