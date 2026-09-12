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

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class VerifiedStagingInstall {
    private final Path normalizedRoot;
    private final Path realRoot;
    private final String rootIdentity;
    private final Path normalizedStagingRoot;
    private final Path realStagingRoot;
    private final String stagingRootIdentity;
    private final Path normalizedSource;
    private final Path realSource;
    private final String sourceIdentity;
    private final String id;
    private final String url;
    private final String versionId;
    private final String versionNumber;
    private final String sha1;
    private final String desiredHash;
    private final Entry committedEntrySnapshot;
    private final boolean legacyReplacementAuthorized;
    private final DatapackStagingGuard.LegacyStagingSnapshot legacyStagingSnapshot;
    private boolean consumed;

    VerifiedStagingInstall(
            Path normalizedRoot,
            Path normalizedStagingRoot,
            Path normalizedSource,
            Entry entry,
            String desiredHash,
            Entry committedEntrySnapshot,
            boolean legacyReplacementAuthorized,
            DatapackStagingGuard.LegacyStagingSnapshot legacyStagingSnapshot
    ) throws IOException {
        this.normalizedRoot = normalizedRoot;
        this.realRoot = normalizedRoot.toRealPath();
        this.rootIdentity = DatapackSupport.directoryIdentity(normalizedRoot.toFile());
        this.normalizedStagingRoot = normalizedStagingRoot;
        this.realStagingRoot = normalizedStagingRoot.toRealPath();
        this.stagingRootIdentity = DatapackSupport.directoryIdentity(normalizedStagingRoot.toFile());
        this.normalizedSource = normalizedSource;
        this.realSource = normalizedSource.toRealPath();
        this.sourceIdentity = DatapackSupport.directoryIdentity(normalizedSource.toFile());
        this.id = entry.id;
        this.url = entry.url;
        this.versionId = entry.versionId;
        this.versionNumber = entry.versionNumber;
        this.sha1 = entry.sha1;
        this.desiredHash = desiredHash;
        this.committedEntrySnapshot = committedEntrySnapshot == null ? null : DatapackManifestStore.copyEntry(committedEntrySnapshot);
        this.legacyReplacementAuthorized = legacyReplacementAuthorized;
        this.legacyStagingSnapshot = legacyStagingSnapshot;
    }

    Path stagingRoot() {
        return normalizedStagingRoot;
    }

    boolean hasStablePathIdentities() {
        return !rootIdentity.isEmpty()
                && !stagingRootIdentity.isEmpty()
                && !sourceIdentity.isEmpty();
    }

    boolean isCanonicalInstall(File installRoot, File target) {
        Path suppliedRoot = installRoot.toPath().toAbsolutePath().normalize();
        Path suppliedTarget = target.toPath().toAbsolutePath().normalize();
        return suppliedRoot.equals(normalizedStagingRoot)
                && suppliedTarget.equals(normalizedStagingRoot.resolve(id).normalize());
    }

    void verifyStagingRoot() throws IOException {
        verifyPathIdentity(normalizedRoot, realRoot, rootIdentity, "datapack storage root");
        verifyPathIdentity(
                normalizedStagingRoot, realStagingRoot, stagingRootIdentity, "datapack staging root");
        if (!Objects.equals(normalizedStagingRoot.getParent(), normalizedRoot)
                || !Files.isSameFile(normalizedStagingRoot.getParent(), normalizedRoot)) {
            throw new IOException("Verified datapack staging root changed identity");
        }
    }

    boolean consume(
            File source,
            File installRoot,
            File target,
            Entry entry,
            String stagedHash
    ) throws IOException {
        if (consumed) {
            throw new IOException("Verified datapack staging authorization was already consumed");
        }
        consumed = true;
        Path suppliedRoot = installRoot.toPath().toAbsolutePath().normalize();
        Path suppliedTarget = target.toPath().toAbsolutePath().normalize();
        if (!suppliedRoot.equals(normalizedStagingRoot)
                || !suppliedTarget.equals(normalizedStagingRoot.resolve(id).normalize())) {
            throw new IOException("Verified datapack staging authorization was paired with a different path");
        }
        verifyAuthority(source, entry, stagedHash);
        return legacyReplacementAuthorized;
    }

    boolean authorizeLegacyWorldReplacement(
            File source,
            File installRoot,
            File target,
            Entry entry,
            String stagedHash,
            String currentHash,
            String currentMarkerHash
    ) throws IOException {
        if (legacyStagingSnapshot == null
                || !"absent".equals(currentMarkerHash)
                || !Objects.equals(currentHash, legacyStagingSnapshot.contentHash())
                || legacyStagingSnapshot.targetIdentity().isEmpty()) {
            return false;
        }
        Path suppliedRoot = installRoot.toPath().toAbsolutePath().normalize();
        Path suppliedTarget = target.toPath().toAbsolutePath().normalize();
        if (!Objects.equals(suppliedTarget.getParent(), suppliedRoot)
                || !Objects.equals(suppliedTarget.getFileName().toString(), id)
                || Files.isSameFile(suppliedRoot, normalizedStagingRoot)
                || Files.isSameFile(suppliedTarget, legacyStagingSnapshot.normalizedTarget())) {
            return false;
        }
        verifyAuthority(source, entry, stagedHash);
        verifyLegacyWorldSnapshot();
        return true;
    }

    private void verifyAuthority(File source, Entry entry, String stagedHash) throws IOException {
        verifyAuthorityState();
        Path suppliedSource = source.toPath().toAbsolutePath().normalize();
        if (!suppliedSource.equals(normalizedSource)) {
            throw new IOException("Verified datapack staging authorization was paired with a different path");
        }
        if (!Objects.equals(entry.id, id)
                || !Objects.equals(entry.url, url)
                || !Objects.equals(entry.versionId, versionId)
                || !Objects.equals(entry.versionNumber, versionNumber)
                || !Objects.equals(entry.sha1, sha1)
                || !Objects.equals(stagedHash, desiredHash)) {
            throw new IOException("Verified datapack staging authorization was paired with different metadata");
        }
    }

    private void verifyAuthorityState() throws IOException {
        verifyStagingRoot();
        verifyPathIdentity(normalizedSource, realSource, sourceIdentity, "verified datapack extraction");
        Manifest committedManifest = DatapackManifestStore.readCommittedManifest(normalizedRoot.toFile());
        Entry committed = committedManifest.findById(id);
        if (!manifestEntrySnapshotMatches(committed, committedEntrySnapshot)) {
            throw new IOException("Committed datapack staging authority changed before installation");
        }
        if (committedEntrySnapshot != null && !legacyReplacementAuthorized) {
            throw new IOException("Committed datapack staging authority conflicts with " + id);
        }
    }

    private static boolean manifestEntrySnapshotMatches(Entry current, Entry expected) {
        if (current == null || expected == null) {
            return current == expected;
        }
        return Objects.equals(current.id, expected.id)
                && Objects.equals(current.url, expected.url)
                && Objects.equals(current.versionId, expected.versionId)
                && Objects.equals(current.versionNumber, expected.versionNumber)
                && Objects.equals(current.sha1, expected.sha1)
                && Objects.equals(current.filename, expected.filename)
                && Objects.equals(current.etag, expected.etag)
                && Objects.equals(current.lastModified, expected.lastModified)
                && current.installedEpoch == expected.installedEpoch
                && current.structuresImported == expected.structuresImported
                && DatapackManifestStore.copyList(current.structureKeys).equals(DatapackManifestStore.copyList(expected.structureKeys))
                && DatapackManifestStore.copyList(current.templateKeys).equals(DatapackManifestStore.copyList(expected.templateKeys))
                && Objects.equals(current.importedTargets, expected.importedTargets)
                && Objects.equals(current.importAttempts, expected.importAttempts)
                && Objects.equals(current.importedBundles, expected.importedBundles);
    }

    void verifyLegacyWorldSnapshot() throws IOException {
        verifyAuthorityState();
        if (legacyStagingSnapshot == null || legacyStagingSnapshot.targetIdentity().isEmpty()) {
            throw new IOException("Missing stable canonical legacy datapack staging snapshot for " + id);
        }
        if (!Objects.equals(legacyStagingSnapshot.normalizedTarget().getParent(), normalizedStagingRoot)
                || !Files.isSameFile(
                legacyStagingSnapshot.normalizedTarget().getParent(), normalizedStagingRoot)
                || !DatapackScratchRecovery.sameScratchVolume(
                legacyStagingSnapshot.normalizedTarget(), normalizedStagingRoot)) {
            throw new IOException("Changed or unsafe canonical legacy datapack staging target for " + id);
        }
        DatapackInstallPlanner.verifyDirectorySnapshot(
                legacyStagingSnapshot.normalizedTarget().toFile(),
                normalizedStagingRoot.toFile(),
                legacyStagingSnapshot.contentHash(),
                legacyStagingSnapshot.markerHash(),
                legacyStagingSnapshot.targetIdentity(),
                "canonical legacy datapack staging target"
        );
        if (!Objects.equals(
                legacyStagingSnapshot.normalizedTarget().toRealPath(),
                legacyStagingSnapshot.realTarget())) {
            throw new IOException("Changed canonical legacy datapack staging target identity for " + id);
        }
    }

    private static void verifyPathIdentity(
            Path normalized,
            Path expectedReal,
            String expectedIdentity,
            String purpose
    ) throws IOException {
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
                || !Objects.equals(normalized.toRealPath(), expectedReal)
                || (!expectedIdentity.isEmpty()
                && !Objects.equals(DatapackSupport.directoryIdentity(normalized.toFile()), expectedIdentity))) {
            throw new IOException("Changed or unsafe " + purpose + " " + normalized);
        }
    }
}
