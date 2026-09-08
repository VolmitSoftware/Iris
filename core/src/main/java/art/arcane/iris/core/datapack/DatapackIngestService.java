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
import art.arcane.iris.core.IrisStartupValidation;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.ServerConfigurator;
import art.arcane.iris.core.datapack.ModrinthResolver.ResolvedDatapack;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.MinecraftVersion;
import art.arcane.iris.core.project.IrisProject;
import art.arcane.iris.core.project.IrisCodeWorkspace;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.io.IO;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class DatapackIngestService {

    private DatapackIngestService() {
    }

    public static Report ingestAll(VolmitSender sender, boolean restart) {
        return ingest(sender, collectConfiguredImports(), restart);
    }

    public static void autoIngestOnStartup() {
        StartupValidationOutcome outcome = validateOnStartup();
        if (outcome == StartupValidationOutcome.READY) {
            runPostStartupTasks();
        }
    }

    public static StartupValidationOutcome validateOnStartup() {
        DatapackStartupValidation.activeStartupValidation = null;
        IrisStartupValidation.beginDatapackValidation();
        KList<String> configured = collectConfiguredImports();
        List<String> urls = configured.stream().sorted().toList();
        boolean autoIngest = IrisSettings.get().getGeneral().autoIngestDatapacks;
        boolean stripOverrides = DatapackPackMetadata.resolveStripOverrides();
        String mcVersion = serverMcVersion();
        int irisVersion = IrisPlatforms.get().irisVersionNumber();
        File root = IrisPlatforms.get().dataFolder("datapacks");
        KList<File> worldFolders = ServerConfigurator.getDatapacksFolder();
        Path cacheFile = new File(root, DatapackStartupValidation.STARTUP_VALIDATION_CACHE).toPath();

        try {
            StartupValidationCache cached = DatapackStartupValidation.readStartupValidationCache(cacheFile);
            if (DatapackStartupValidation.startupValidationContextMatches(
                    cached, mcVersion, irisVersion, autoIngest, stripOverrides, urls)) {
                String localFingerprint = DatapackStartupValidation.startupValidationFingerprint(root, worldFolders, urls);
                if (DatapackStartupValidation.startupValidationCacheMatches(
                        cached,
                        mcVersion,
                        irisVersion,
                        autoIngest,
                        stripOverrides,
                        urls,
                        localFingerprint)) {
                    DatapackStartupValidation.activeStartupValidation = cached;
                    IrisLogging.info("External datapacks match the persisted startup validation; remote resolution and full revalidation were skipped.");
                    IrisStartupValidation.markDatapacksReady();
                    return StartupValidationOutcome.READY;
                }
            }
        } catch (IOException | RuntimeException exception) {
            IrisLogging.warn("Persisted external datapack validation could not be reused: "
                    + DatapackSupport.failureMessage(exception));
        }

        if (autoIngest && !configured.isEmpty()) {
            IrisLogging.info("Validating " + configured.size()
                    + " configured external datapack import(s) before player admission...");
            Report report = ingest(null, configured, true, true);
            if (!report.getFailed().isEmpty()) {
                String failure = report.getFailed().getFirst();
                IrisStartupValidation.markDatapacksInvalid(failure);
                return StartupValidationOutcome.FAILED;
            }
            StartupValidationOutcome outcome = report.changed()
                    ? StartupValidationOutcome.RESTART_REQUIRED
                    : StartupValidationOutcome.READY;
            DatapackStartupValidation.activeStartupValidation = DatapackStartupValidation.cacheStartupValidation(root, worldFolders, cacheFile, mcVersion, irisVersion,
                    autoIngest, stripOverrides, urls);
            if (outcome == StartupValidationOutcome.RESTART_REQUIRED) {
                IrisStartupValidation.requireRestart(
                        "Iris installed updated external datapacks; restart must complete before player admission or world creation.");
            } else {
                IrisStartupValidation.markDatapacksReady();
            }
            return outcome;
        }

        ReapplyOutcome reapply = reapplyFromStaging(worldFolders);
        if (!reapply.succeeded()) {
            String failure = reapply.failure()
                    .map(DatapackSupport::failureMessage)
                    .orElse("External datapack recovery failed.");
            IrisStartupValidation.markDatapacksInvalid(failure);
            return StartupValidationOutcome.FAILED;
        }
        DatapackStartupValidation.activeStartupValidation = DatapackStartupValidation.cacheStartupValidation(root, worldFolders, cacheFile, mcVersion, irisVersion,
                autoIngest, stripOverrides, urls);
        if (reapply.changed()) {
            IrisStartupValidation.requireRestart(
                    "Iris repaired external datapack files; restart must complete before player admission or world creation.");
            return StartupValidationOutcome.RESTART_REQUIRED;
        }
        IrisStartupValidation.markDatapacksReady();
        return StartupValidationOutcome.READY;
    }

    public static void runPostStartupTasks() {
        refreshWorkspaces();
        boolean maintenanceChanged = DatapackStructureImports.autoImportDatapackStructures();
        DatapackStartupValidation.refreshStartupValidationAfterMaintenance(maintenanceChanged);
    }

    static String serverMcVersion() {
        return serverMcVersion(Bukkit.getServer());
    }

    static String serverMcVersion(Server server) {
        MinecraftVersion detected = MinecraftVersion.detect(server);
        return detected == null ? null : detected.value();
    }

    public static void refreshWorkspaces() {
        try (Stream<IrisData> stream = ServerConfigurator.allPacks()) {
            stream.forEach(DatapackIngestService::refreshWorkspace);
        }
    }

    public static void refreshWorkspace(IrisData data) {
        if (data == null || !DatapackImportSources.hasImports(data)) {
            return;
        }
        try {
            new IrisCodeWorkspace(new IrisProject(data.getDataFolder())).updateWorkspace();
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    public static Report ingest(VolmitSender sender, KList<String> urls, boolean restart) {
        return ingest(sender, urls, restart, false);
    }

    /**
     * Only startup validation owns the global player-admission gate (gateAdmission=true). The
     * runtime admin command must never flip it: a transient download failure there would lock
     * every login until restart, and the PENDING window would close logins for the whole
     * (potentially very slow) download. World-creation safety on the ungated path is carried
     * by the loaded-runtime invalidation plus requireDatapackRestart when files change.
     */
    private static Report ingest(VolmitSender sender, KList<String> urls, boolean restart, boolean gateAdmission) {
        if (gateAdmission) {
            IrisStartupValidation.beginDatapackValidation();
        }
        ServerConfigurator.LoadedDatapackRuntimeInvalidation invalidation =
                ServerConfigurator.invalidateLoadedDatapackRuntime();
        Report report;
        boolean settled = false;
        DatapackTransactions.TRANSACTION_LOCK.lock();
        try {
            report = ingestLocked(sender, urls, restart);
            settled = true;
        } finally {
            DatapackTransactions.TRANSACTION_LOCK.unlock();
            if (!settled && gateAdmission) {
                // Never strand the admission gate at PENDING on an unexpected throw.
                IrisStartupValidation.markDatapacksInvalid(
                        "External datapack ingest failed unexpectedly; check the log above.");
            }
        }
        if (!report.changed() && report.getFailed().isEmpty()) {
            ServerConfigurator.restoreLoadedDatapackRuntimeIfUnchanged(invalidation);
        } else if (report.changed()) {
            if (restart) {
                ServerConfigurator.restart();
            } else {
                ServerConfigurator.requireDatapackRestart();
            }
        }
        if (gateAdmission) {
            if (!report.getFailed().isEmpty()) {
                IrisStartupValidation.markDatapacksInvalid(report.getFailed().getFirst());
            } else if (!report.changed()) {
                IrisStartupValidation.markDatapacksReady();
            }
        }
        return report;
    }

    private static Report ingestLocked(VolmitSender sender, KList<String> urls, boolean restart) {
        Report report = new Report();
        if (urls == null || urls.isEmpty()) {
            DatapackSupport.message(sender, C.YELLOW + "No external datapacks found. Add an HTTP(S) or file URL to a dimension's 'datapackImports', or place a ZIP in Iris/datapacks/imports, then run /iris datapack ingest.");
            return report;
        }

        File root = IrisPlatforms.get().dataFolder("datapacks");
        File cacheDir = new File(root, "cache");
        File stagingDir = new File(root, "staging");
        try {
            DatapackSupport.ensureScratchDirectory(cacheDir, "datapack download cache");
            DatapackSupport.ensureScratchDirectory(stagingDir, "datapack staging");
        } catch (IOException e) {
            report.failed.add("local storage - " + e.getMessage());
            DatapackSupport.message(sender, C.RED + "Datapack ingest failed: " + e.getMessage());
            IrisLogging.reportError(e);
            return report;
        }

        KList<File> worldFolders = ServerConfigurator.getDatapacksFolder();
        String mcVersion = serverMcVersion();
        try {
            DatapackScratchRecovery.recoverTransactions(root, worldFolders);
        } catch (IOException e) {
            report.failed.add("transaction recovery - " + e.getMessage());
            DatapackSupport.message(sender, C.RED + "Datapack ingest blocked by incomplete transaction recovery: " + e.getMessage());
            IrisLogging.reportError(e);
            return report;
        }
        Manifest manifest = DatapackManifestStore.readManifest(root);
        boolean stripOverrides = DatapackPackMetadata.resolveStripOverrides();
        List<InstallExecution> installs = new ArrayList<>();

        DatapackSupport.message(sender, C.GRAY + "Ingesting " + C.WHITE + urls.size() + C.GRAY + " datapack import(s)" + (mcVersion == null ? "" : " for MC " + mcVersion) + (stripOverrides ? C.GRAY + " (datapackOverrides=false: minecraft-namespaced structure overrides will be stripped)" : "") + "...");

        for (String url : urls) {
            try {
                ingestSingle(
                        sender,
                        url,
                        mcVersion,
                        cacheDir,
                        stagingDir,
                        worldFolders,
                        manifest,
                        report,
                        stripOverrides,
                        installs
                );
            } catch (Exception e) {
                report.failed.add(url + " - " + e.getMessage());
                DatapackSupport.message(sender, C.RED + "  Failed: " + C.WHITE + url + C.RED + " - " + e.getMessage());
                IrisLogging.reportError(e);
            }
        }

        diagnoseConflicts(sender, manifest);
        ManifestWrite manifestWrite = null;
        boolean manifestDurabilityConfirmed = false;
        try {
            for (InstallExecution install : installs) {
                DatapackInstall.verifyInstallExecution(install);
            }
            manifestWrite = DatapackManifestStore.prepareManifestWrite(root, manifest);
            manifestWrite.publish();
            manifestDurabilityConfirmed = true;
        } catch (IOException manifestFailure) {
            if (manifestWrite == null || !manifestWrite.published()) {
                DatapackInstall.rollbackInstallExecutions(installs, manifestFailure);
                report.failed.add("manifest - " + manifestFailure.getMessage());
                report.updated.clear();
                report.upToDate.clear();
                report.requiresRestart = false;
                DatapackSupport.message(sender, C.RED + "Datapack ingest rolled back before the manifest commit: "
                        + manifestFailure.getMessage());
                IrisLogging.reportError(manifestFailure);
                return report;
            }
            IrisLogging.reportError("Datapack manifest was published but durability confirmation failed; "
                    + "transaction backups are retained for restart recovery.", manifestFailure);
        } finally {
            if (manifestWrite != null) {
                try {
                    manifestWrite.discard();
                } catch (IOException cleanupFailure) {
                    IrisLogging.reportError("Datapack manifest staging cleanup failed.", cleanupFailure);
                }
            }
        }
        if (manifestDurabilityConfirmed) {
            for (InstallExecution install : installs) {
                try {
                    DatapackInstall.finishInstallExecution(install);
                } catch (IOException cleanupFailure) {
                    IrisLogging.reportError("Datapack install committed but transaction cleanup requires restart recovery.",
                            cleanupFailure);
                }
            }
        }
        DatapackArchive.pruneCache(cacheDir);
        DatapackSupport.message(sender, C.GREEN + "Datapack ingest complete: " + C.WHITE + report.updated.size() + C.GREEN + " updated, " + C.WHITE + report.upToDate.size() + C.GREEN + " up to date, " + C.WHITE + report.failed.size() + C.GREEN + " failed.");

        if (report.changed()) {
            DatapackSupport.message(sender, C.YELLOW + "New datapack structures were installed. A server restart is required for them to register and generate.");
            DatapackSupport.message(sender, C.GRAY + "After the restart they generate natively in Iris dimensions that declare their source URL; ZIPs from Iris/datapacks/imports are enabled for every Iris dimension. To get editable Iris copies (jigsaw pools, pieces & objects written into the pack) run /iris structure import <dimension>, or set general.autoImportDatapackStructures=true to do it on every ingest. Place any registered key directly with a 'structures' placement using nativeStructures.");
            DatapackSupport.message(sender, C.GRAY + "Datapacks replace matching vanilla structure keys by default. Set 'importedStructures.datapackOverrides' to false to keep minecraft-namespaced structure definitions untouched; deny non-minecraft datapack and mod structure families with importedStructures.disabled or complete keys with importedStructures.disabledExact.");
            if (!restart) {
                DatapackSupport.message(sender, C.GRAY + "Run with restart=true to restart now, or restart manually. After restart, run /iris structure list <dimension> to see the new keys.");
            }
        }

        return report;
    }

    public static ReapplyOutcome reapplyFromStaging(KList<File> worldFolders) {
        ServerConfigurator.LoadedDatapackRuntimeInvalidation invalidation =
                ServerConfigurator.invalidateLoadedDatapackRuntime();
        ReapplyOutcome outcome;
        DatapackTransactions.TRANSACTION_LOCK.lock();
        try {
            outcome = DatapackStagingReapply.reapplyFromStagingLocked(worldFolders);
        } finally {
            DatapackTransactions.TRANSACTION_LOCK.unlock();
        }
        if (outcome.succeeded() && !outcome.changed()) {
            ServerConfigurator.restoreLoadedDatapackRuntimeIfUnchanged(invalidation);
        } else if (outcome.changed()) {
            ServerConfigurator.requireDatapackRestart();
        } else {
            IrisStartupValidation.markDatapacksInvalid(outcome.failure()
                    .map(DatapackSupport::failureMessage)
                    .orElse("External datapack recovery failed."));
        }
        return outcome;
    }

    public static boolean remove(VolmitSender sender, String id) {
        ServerConfigurator.LoadedDatapackRuntimeInvalidation invalidation =
                ServerConfigurator.invalidateLoadedDatapackRuntime();
        RemoveOutcome outcome;
        DatapackTransactions.TRANSACTION_LOCK.lock();
        try {
            outcome = DatapackRemoval.removeOutcomeLocked(sender, id);
        } finally {
            DatapackTransactions.TRANSACTION_LOCK.unlock();
        }
        if (outcome == RemoveOutcome.REMOVED) {
            ServerConfigurator.requireDatapackRestart();
        } else if (outcome == RemoveOutcome.REJECTED) {
            // Rejected before any mutation: nothing on disk changed, so the loaded runtime is
            // still valid. Leaving it invalidated forced a full datapack reinstall on every
            // later world creation for the rest of the session.
            ServerConfigurator.restoreLoadedDatapackRuntimeIfUnchanged(invalidation);
        }
        return outcome == RemoveOutcome.REMOVED;
    }

    public static KList<String> collectConfiguredImports() {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        try (Stream<IrisData> stream = ServerConfigurator.allPacks()) {
            stream.forEach(data -> DatapackImportSources.collectImports(data, sources));
        }
        sources.addAll(DatapackImportSources.localDatapackImports());
        KList<String> result = new KList<>();
        result.addAll(sources);
        return result;
    }

    public static Set<String> configuredImports(IrisDimension dimension) {
        Iterable<String> explicit = dimension == null ? List.of() : dimension.getDatapackImports();
        return DatapackImportSources.mergeConfiguredImports(explicit, DatapackImportSources.localDatapackImports());
    }

    public static List<Entry> installed() {
        DatapackTransactions.TRANSACTION_LOCK.lock();
        try {
            File root = IrisPlatforms.get().dataFolder("datapacks");
            try {
                DatapackScratchRecovery.recoverTransactions(root, ServerConfigurator.getDatapacksFolder());
            } catch (IOException e) {
                IrisLogging.reportError("Could not recover datapack transactions before listing installed packs.", e);
                return List.of();
            }
            return List.copyOf(DatapackManifestStore.readManifest(root).entries);
        } finally {
            DatapackTransactions.TRANSACTION_LOCK.unlock();
        }
    }

    public static List<StructureScopeResources> installedStructureScopeResources() throws IOException {
        DatapackTransactions.TRANSACTION_LOCK.lock();
        try {
            File root = IrisPlatforms.get().dataFolder("datapacks");
            Manifest manifest = DatapackManifestStore.readManifest(root);
            KList<File> datapackFolders = ServerConfigurator.getDatapacksFolder();
            List<StructureScopeResources> resources = new ArrayList<>();
            for (Entry entry : manifest.entries) {
                boolean found = false;
                for (File datapackFolder : datapackFolders) {
                    File installedDirectory = new File(datapackFolder, entry.id);
                    if (!Files.exists(installedDirectory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    resources.add(scanInstalledStructureScope(installedDirectory, entry));
                    found = true;
                }
                if (!found) {
                    throw new IOException("Missing installed Iris-managed datapack '" + entry.id + "'");
                }
            }
            return List.copyOf(resources);
        } finally {
            DatapackTransactions.TRANSACTION_LOCK.unlock();
        }
    }

    static StructureScopeResources scanInstalledStructureScope(File directory, Entry entry) throws IOException {
        Path path = directory.toPath();
        if (Files.isSymbolicLink(path)
                || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid installed Iris-managed datapack directory " + directory.getPath());
        }
        DatapackPackMetadata.validatePackMetadata(directory);
        DatapackOwnership.rejectSymbolicLinks(directory);
        Ownership ownership = DatapackOwnership.readOwnership(directory);
        if (!DatapackInstall.ownershipSourceMatches(ownership, entry)) {
            throw new IOException("Installed datapack ownership mismatch at " + directory.getPath());
        }
        if (!Objects.equals(ownership.contentHash, DatapackOwnership.directoryHash(directory))) {
            throw new IOException("Installed Iris-managed datapack is modified or corrupt at "
                    + directory.getPath());
        }
        PackResources resources = DatapackOwnership.scanPackResources(directory);
        return new StructureScopeResources(
                entry.url,
                resources.structureKeys(),
                resources.structureSetKeys());
    }

    private static void ingestSingle(
            VolmitSender sender,
            String url,
            String mcVersion,
            File cacheDir,
            File stagingDir,
            KList<File> worldFolders,
            Manifest manifest,
            Report report,
            boolean stripOverrides,
            List<InstallExecution> installs
    ) throws IOException {
        ResolvedDatapack resolved = ModrinthResolver.resolve(url, mcVersion);
        Entry existing = manifest.find(url);
        String id = existing == null ? DatapackArchive.deriveId(resolved) : existing.id;
        if (DatapackArchive.RESERVED_IDS.contains(id)) {
            throw new IOException("Datapack id '" + id + "' is reserved by Iris");
        }
        Entry idCollision = manifest.findById(id);
        if (idCollision != null && !Objects.equals(idCollision.url, url)) {
            throw new IOException("Datapack id '" + id + "' is already owned by " + idCollision.url);
        }
        File stagedDir = new File(stagingDir, id);
        boolean stageUsable = existing != null && DatapackStagingGuard.isUsableStaging(stagedDir, existing);
        boolean recoveringManagedStaging = existing != null && !stageUsable;
        boolean sameVersion = !resolved.isDirect()
                && existing != null
                && Objects.equals(existing.versionId, resolved.getVersionId())
                && (resolved.getSha1() == null || Objects.equals(existing.sha1, resolved.getSha1()))
                && stageUsable;

        if (sameVersion) {
            InstallExecution execution = DatapackInstall.prepareInstallExecution(
                    stagedDir, worldFolders, existing, stripOverrides, cacheDir.getParentFile());
            installs.add(execution);
            InstallResult installResult = execution.result();
            DatapackStagingGuard.recordInstallResult(
                    sender, report, stagedDir, worldFolders, existing, installResult, resolved.getVersionNumber());
            return;
        }

        DatapackSupport.message(sender, C.GRAY + "  Checking " + C.WHITE + id + C.GRAY + " " + DatapackSupport.safe(resolved.getVersionNumber()) + "...");
        File zip = new File(cacheDir, id + "-" + DatapackSupport.safeFile(resolved.getVersionId()) + ".zip");
        DownloadResult download = DatapackArchive.download(
                resolved.getDownloadUrl(),
                zip,
                resolved.isDirect() && stageUsable ? existing.etag : null,
                resolved.isDirect() && stageUsable ? existing.lastModified : null
        );
        if (download.notModified()) {
            if (!stageUsable) {
                throw new IOException("Remote returned not-modified but Iris staging is missing or corrupt for " + id);
            }
            Entry updated = DatapackManifestStore.copyEntry(existing);
            updated.etag = download.etag();
            updated.lastModified = download.lastModified();
            InstallExecution execution = DatapackInstall.prepareInstallExecution(
                    stagedDir, worldFolders, updated, stripOverrides, cacheDir.getParentFile());
            installs.add(execution);
            InstallResult installResult = execution.result();
            manifest.put(updated);
            DatapackStagingGuard.recordInstallResult(
                    sender, report, stagedDir, worldFolders, updated, installResult, resolved.getVersionNumber());
            return;
        }

        String checksum = DatapackArchive.sha1(zip);
        if (resolved.getSha1() != null && !resolved.getSha1().isBlank() && !resolved.getSha1().equalsIgnoreCase(checksum)) {
            IO.delete(zip);
            throw new IOException("Checksum mismatch for " + id + " (expected " + resolved.getSha1() + ", got " + checksum + ")");
        }

        if (resolved.isDirect() && existing != null && Objects.equals(existing.sha1, checksum) && stageUsable) {
            Entry updated = DatapackManifestStore.copyEntry(existing);
            updated.etag = download.etag();
            updated.lastModified = download.lastModified();
            updated.installedEpoch = System.currentTimeMillis();
            InstallExecution execution = DatapackInstall.prepareInstallExecution(
                    stagedDir, worldFolders, updated, stripOverrides, cacheDir.getParentFile());
            installs.add(execution);
            InstallResult installResult = execution.result();
            DatapackOwnership.writeOwnership(stagedDir, updated);
            manifest.put(updated);
            DatapackStagingGuard.recordInstallResult(
                    sender, report, stagedDir, worldFolders, updated, installResult, resolved.getVersionNumber());
            return;
        }

        Entry entry = existing != null ? DatapackManifestStore.copyEntry(existing) : new Entry();
        entry.url = url;
        entry.id = id;
        entry.versionId = resolved.getVersionId();
        entry.versionNumber = resolved.getVersionNumber();
        entry.sha1 = checksum;
        entry.filename = resolved.getFileName();
        entry.etag = download.etag();
        entry.lastModified = download.lastModified();
        entry.installedEpoch = System.currentTimeMillis();
        entry.structuresImported = false;
        File extractedDir = DatapackArchive.extractArchive(zip, stagingDir, entry);
        InstallExecution execution;
        try {
            VerifiedStagingInstall verifiedStagingInstall =
                    DatapackStagingGuard.authorizeVerifiedStagingInstall(
                            cacheDir.getParentFile(), stagingDir, extractedDir, entry);
            execution = DatapackInstall.prepareInstallExecution(
                    extractedDir, worldFolders, entry, stripOverrides, cacheDir.getParentFile(),
                    verifiedStagingInstall);
        } finally {
            DatapackArchive.cleanupExtractedStaging(extractedDir);
        }
        installs.add(execution);
        InstallResult installResult = execution.result();
        DatapackStagingReapply.recordInstallMetadata(stagedDir, worldFolders, entry);
        manifest.put(entry);

        report.updated.add(id + " (" + DatapackSupport.safe(resolved.getVersionNumber()) + ")");
        if (freshInstallRequiresRestart(installResult.changed(), recoveringManagedStaging)) {
            report.requiresRestart = true;
        }
        DatapackSupport.message(sender, C.GREEN + "  Installed " + C.WHITE + id + C.GREEN + " " + DatapackSupport.safe(resolved.getVersionNumber()));
    }

    static boolean freshInstallRequiresRestart(boolean contentChanged, boolean recoveringManagedStaging) {
        return contentChanged || recoveringManagedStaging;
    }

    private static void diagnoseConflicts(VolmitSender sender, Manifest manifest) {
        Map<String, List<String>> owners = new TreeMap<>();
        for (Entry entry : manifest.entries) {
            for (String key : DatapackManifestStore.copyList(entry.structureKeys)) {
                owners.computeIfAbsent("structure " + key, ignored -> new ArrayList<>()).add(entry.id);
            }
            for (String key : DatapackManifestStore.copyList(entry.templateKeys)) {
                owners.computeIfAbsent("template " + key, ignored -> new ArrayList<>()).add(entry.id);
            }
        }
        int conflicts = 0;
        for (Map.Entry<String, List<String>> resource : owners.entrySet()) {
            if (resource.getValue().size() < 2) {
                continue;
            }
            conflicts++;
            if (conflicts <= 20) {
                DatapackSupport.message(sender, C.YELLOW + "  Registry conflict: " + C.WHITE + resource.getKey()
                        + C.YELLOW + " is supplied by " + String.join(", ", resource.getValue()));
            }
        }
        if (conflicts > 0) {
            DatapackSupport.message(sender, C.YELLOW + "Detected " + conflicts + " external datapack registry conflict(s). Minecraft's enabled-pack order in level.dat determines precedence; datapackImports order does not.");
        }
        if (conflicts > 20) {
            DatapackSupport.message(sender, C.GRAY + "  " + (conflicts - 20) + " additional conflict(s) omitted.");
        }
    }

    public static final class Report {
        final KList<String> updated = new KList<>();
        final KList<String> upToDate = new KList<>();
        private final KList<String> failed = new KList<>();
        boolean requiresRestart;

        public boolean changed() {
            return requiresRestart;
        }

        public KList<String> getUpdated() {
            return updated;
        }

        public KList<String> getUpToDate() {
            return upToDate;
        }

        public KList<String> getFailed() {
            return failed;
        }
    }

    public static final class Entry {
        public String url;
        public String id;
        public String versionId;
        public String versionNumber;
        public String sha1;
        public String filename;
        public String etag;
        public String lastModified;
        public long installedEpoch;
        public boolean structuresImported;
        public String stagingMetadata = "";
        public List<String> structureKeys = new ArrayList<>();
        public List<String> templateKeys = new ArrayList<>();
        public Map<String, String> installMetadata = new HashMap<>();
        public Map<String, String> importedTargets = new HashMap<>();
        public Map<String, String> importAttempts = new HashMap<>();
        public Map<String, Map<String, String>> importedBundles = new HashMap<>();
    }

    public enum StartupValidationOutcome {
        READY,
        RESTART_REQUIRED,
        FAILED
    }

    public record ReapplyOutcome(
            ReapplyStatus status,
            Optional<Throwable> failure
    ) {
        public ReapplyOutcome {
            status = Objects.requireNonNull(status, "External datapack reapply status");
            failure = Objects.requireNonNull(failure, "External datapack reapply failure");
            if (status == ReapplyStatus.FAILED && failure.isEmpty()) {
                throw new IllegalArgumentException("Failed external datapack reapply requires a cause");
            }
            if (status != ReapplyStatus.FAILED && failure.isPresent()) {
                throw new IllegalArgumentException("Successful external datapack reapply cannot carry a cause");
            }
        }

        public static ReapplyOutcome success(boolean recovered, boolean repaired) {
            ReapplyStatus status;
            if (recovered && repaired) {
                status = ReapplyStatus.RECOVERED_AND_REPAIRED;
            } else if (recovered) {
                status = ReapplyStatus.RECOVERED;
            } else if (repaired) {
                status = ReapplyStatus.REPAIRED;
            } else {
                status = ReapplyStatus.UNCHANGED;
            }
            return new ReapplyOutcome(status, Optional.empty());
        }

        public static ReapplyOutcome failed(Throwable failure) {
            return new ReapplyOutcome(
                    ReapplyStatus.FAILED,
                    Optional.of(Objects.requireNonNull(failure, "External datapack reapply failure cause")));
        }

        public boolean succeeded() {
            return status != ReapplyStatus.FAILED;
        }

        public boolean changed() {
            return recovered() || repaired();
        }

        public boolean recovered() {
            return status == ReapplyStatus.RECOVERED
                    || status == ReapplyStatus.RECOVERED_AND_REPAIRED;
        }

        public boolean repaired() {
            return status == ReapplyStatus.REPAIRED
                    || status == ReapplyStatus.RECOVERED_AND_REPAIRED;
        }
    }

    public enum ReapplyStatus {
        UNCHANGED,
        RECOVERED,
        REPAIRED,
        RECOVERED_AND_REPAIRED,
        FAILED
    }

    public record StructureScopeResources(
            String source,
            List<String> structureKeys,
            List<String> structureSetKeys
    ) {
    }
}
