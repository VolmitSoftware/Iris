package art.arcane.iris.pack.datapack;

import art.arcane.iris.pack.PackFingerprints;

import org.junit.Rule;
import org.junit.Test;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ServerConfiguratorDatapackFingerprintTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void computePackFingerprintReturnsSameHashForUnchangedFiles() throws Exception {
        File packsDir = tmp.newFolder("packs");
        File dimFile = new File(packsDir, "testpack/dimensions/overworld.json");
        dimFile.getParentFile().mkdirs();
        dimFile.createNewFile();

        String fp1 = PackFingerprints.computePackFingerprint(packsDir);
        String fp2 = PackFingerprints.computePackFingerprint(packsDir);

        assertNotNull("Fingerprint must not be null", fp1);
        assertEquals("Same unchanged files must produce identical fingerprint", fp1, fp2);
    }

    @Test
    public void computePackFingerprintIgnoresMetadataOnlyChanges() throws Exception {
        File packsDir = tmp.newFolder("packs");
        File dimFile = new File(packsDir, "testpack/dimensions/overworld.json");
        dimFile.getParentFile().mkdirs();
        dimFile.createNewFile();

        String fp1 = PackFingerprints.computePackFingerprint(packsDir);
        dimFile.setLastModified(dimFile.lastModified() + 2000L);
        String fp2 = PackFingerprints.computePackFingerprint(packsDir);

        assertEquals("Metadata-only changes must not alter a content fingerprint", fp1, fp2);
    }

    @Test
    public void computePackFingerprintDetectsEqualSizeContentWithRestoredMtime() throws Exception {
        File packsDir = tmp.newFolder("content-packs");
        Path dimension = packsDir.toPath().resolve("testpack/dimensions/overworld.json");
        Files.createDirectories(dimension.getParent());
        Files.writeString(dimension, "aaaa", StandardCharsets.UTF_8);
        FileTime originalMtime = Files.getLastModifiedTime(dimension);
        String before = PackFingerprints.computePackFingerprint(packsDir);

        Files.writeString(dimension, "bbbb", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(dimension, originalMtime);
        String after = PackFingerprints.computePackFingerprint(packsDir);

        assertNotEquals("Equal-size content changes must alter the fingerprint", before, after);
    }

    @Test
    public void contentSnapshotPublishesExactAggregateAndPerPackFingerprints() throws Exception {
        File packsDir = tmp.newFolder("content-snapshot-packs");
        Path alphaPack = packsDir.toPath().resolve("alpha");
        Path betaPack = packsDir.toPath().resolve("beta");
        Path alphaDimension = alphaPack.resolve("dimensions/alpha.json");
        Path betaDimension = betaPack.resolve("dimensions/beta.json");
        Files.createDirectories(alphaDimension.getParent());
        Files.createDirectories(betaDimension.getParent());
        Files.writeString(alphaDimension, "alpha-a", StandardCharsets.UTF_8);
        Files.writeString(betaDimension, "beta-a", StandardCharsets.UTF_8);

        PackFingerprints.PackContentSnapshot before =
                PackFingerprints.computePackContentSnapshot(packsDir);

        assertEquals(PackFingerprints.computePackFingerprint(packsDir), before.content());
        assertEquals(PackFingerprints.computePackTreeFingerprint(alphaPack.toFile()),
                before.packContents().get("alpha"));
        assertEquals(PackFingerprints.computePackTreeFingerprint(betaPack.toFile()),
                before.packContents().get("beta"));

        Files.writeString(alphaDimension, "alpha-b", StandardCharsets.UTF_8);
        PackFingerprints.PackContentSnapshot after =
                PackFingerprints.computePackContentSnapshot(packsDir);

        assertNotEquals(before.content(), after.content());
        assertNotEquals(before.packContents().get("alpha"), after.packContents().get("alpha"));
        assertEquals(before.packContents().get("beta"), after.packContents().get("beta"));
    }

    @Test
    public void computePackFingerprintChangesWhenFileIsAdded() throws Exception {
        File packsDir = tmp.newFolder("packs");
        File dimDir = new File(packsDir, "testpack/dimensions");
        dimDir.mkdirs();
        File dimFile = new File(dimDir, "overworld.json");
        dimFile.createNewFile();

        String fp1 = PackFingerprints.computePackFingerprint(packsDir);
        File extraFile = new File(dimDir, "nether.json");
        extraFile.createNewFile();
        String fp2 = PackFingerprints.computePackFingerprint(packsDir);

        assertNotEquals("Adding a file must produce a different fingerprint", fp1, fp2);
    }

    @Test
    public void computePackFingerprintExcludesHiddenTransactionStages() throws Exception {
        File packsDir = tmp.newFolder("hidden-packs");
        Path visible = packsDir.toPath().resolve("testpack/dimensions/overworld.json");
        Path hidden = packsDir.toPath().resolve(".iris-import-123/dimensions/overworld.json");
        Files.createDirectories(visible.getParent());
        Files.createDirectories(hidden.getParent());
        Files.writeString(visible, "visible", StandardCharsets.UTF_8);
        Files.writeString(hidden, "stage-one", StandardCharsets.UTF_8);
        String before = PackFingerprints.computePackFingerprint(packsDir);

        Files.writeString(hidden, "stage-two", StandardCharsets.UTF_8);

        assertEquals(before, PackFingerprints.computePackFingerprint(packsDir));
    }

    @Test
    public void perPackFingerprintIncludesHiddenFilesCopiedIntoWorldSnapshots() throws Exception {
        File packsDir = tmp.newFolder("hidden-pack-content");
        Path pack = packsDir.toPath().resolve("testpack");
        Path visible = pack.resolve("dimensions/overworld.json");
        Path hidden = pack.resolve("dimensions/.broken.json");
        Files.createDirectories(visible.getParent());
        Files.writeString(visible, "visible", StandardCharsets.UTF_8);
        Files.writeString(hidden, "hidden-one", StandardCharsets.UTF_8);
        PackFingerprints.PackContentSnapshot before =
                PackFingerprints.computePackContentSnapshot(packsDir);

        Files.writeString(hidden, "hidden-two", StandardCharsets.UTF_8);
        PackFingerprints.PackContentSnapshot after =
                PackFingerprints.computePackContentSnapshot(packsDir);

        assertNotEquals(before.content(), after.content());
        assertNotEquals(
                before.packContents().get("testpack"),
                after.packContents().get("testpack"));
        assertEquals(
                after.packContents().get("testpack"),
                PackFingerprints.computePackTreeFingerprint(pack.toFile()));
    }

    @Test
    public void computePackFingerprintIgnoresGeneratedCodeWorkspaceFiles() throws Exception {
        File packsDir = tmp.newFolder("workspace-packs");
        Path dimension = packsDir.toPath().resolve("overworld/dimensions/overworld.json");
        Files.createDirectories(dimension.getParent());
        Files.writeString(dimension, "authored", StandardCharsets.UTF_8);
        String before = PackFingerprints.computePackFingerprint(packsDir);
        String packBefore = PackFingerprints.computePackTreeFingerprint(
                packsDir.toPath().resolve("overworld").toFile());

        Path workspace = packsDir.toPath().resolve("overworld/overworld.code-workspace");
        Files.writeString(workspace, "{\"folders\":[]}", StandardCharsets.UTF_8);

        assertEquals("Iris-generated workspace files must not alter the fingerprint",
                before, PackFingerprints.computePackFingerprint(packsDir));

        Files.writeString(workspace, "{\"folders\":[{\"path\":\".\"}]}", StandardCharsets.UTF_8);

        assertEquals("Reordered workspace bytes must not alter the fingerprint",
                before, PackFingerprints.computePackFingerprint(packsDir));

        Path schema = packsDir.toPath().resolve("overworld/.iris/schema/dimension.json");
        Path repositoryObject = packsDir.toPath().resolve("overworld/.git/objects/blob");
        Files.createDirectories(schema.getParent());
        Files.createDirectories(repositoryObject.getParent());
        Files.writeString(schema, "generated schema", StandardCharsets.UTF_8);
        Files.writeString(repositoryObject, "repository metadata", StandardCharsets.UTF_8);

        assertEquals(before, PackFingerprints.computePackFingerprint(packsDir));
        assertEquals(packBefore, PackFingerprints.computePackTreeFingerprint(
                packsDir.toPath().resolve("overworld").toFile()));
    }

    @Test
    public void fingerprintsIgnoreNestedFinderMetadata() throws Exception {
        File packsDir = tmp.newFolder("finder-metadata-packs");
        Path pack = packsDir.toPath().resolve("overworld");
        Path dimension = pack.resolve("dimensions/overworld.json");
        Path metadata = pack.resolve("objects/nested/.DS_Store");
        Files.createDirectories(dimension.getParent());
        Files.createDirectories(metadata.getParent());
        Files.writeString(dimension, "authored", StandardCharsets.UTF_8);
        PackFingerprints.PackContentSnapshot before = PackFingerprints.computePackContentSnapshot(packsDir);
        String metadataBefore = PackFingerprints.computePackMetadataDigest(packsDir);

        for (String content : new String[]{"initial Finder layout", "updated Finder layout with different length"}) {
            Files.writeString(metadata, content, StandardCharsets.UTF_8);
            PackFingerprints.PackContentSnapshot after = PackFingerprints.computePackContentSnapshot(packsDir);
            assertEquals(before, after);
            assertEquals(before.packContents().get("overworld"),
                    PackFingerprints.computePackTreeFingerprint(pack.toFile()));
            assertEquals(metadataBefore, PackFingerprints.computePackMetadataDigest(packsDir));
        }
    }

    @Test
    public void fingerprintsStillDetectNestedResourceChangesAlongsideFinderMetadata() throws Exception {
        File packsDir = tmp.newFolder("finder-resource-packs");
        Path pack = packsDir.toPath().resolve("overworld");
        Path resource = pack.resolve("objects/nested/tree.iob");
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, "aaaa", StandardCharsets.UTF_8);
        Files.writeString(resource.resolveSibling(".DS_Store"), "Finder layout", StandardCharsets.UTF_8);
        FileTime originalMtime = Files.getLastModifiedTime(resource);
        PackFingerprints.PackContentSnapshot before = PackFingerprints.computePackContentSnapshot(packsDir);

        Files.writeString(resource, "bbbb", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(resource, originalMtime);
        PackFingerprints.PackContentSnapshot after = PackFingerprints.computePackContentSnapshot(packsDir);

        assertNotEquals(before.content(), after.content());
        assertNotEquals(before.packContents().get("overworld"), after.packContents().get("overworld"));
        assertEquals(after.packContents().get("overworld"),
                PackFingerprints.computePackTreeFingerprint(pack.toFile()));
    }

    @Test
    public void resolvePackFingerprintReusesCachedContentWhileMetadataIsUnchanged() throws Exception {
        File packsDir = tmp.newFolder("two-tier-packs");
        Path dimension = packsDir.toPath().resolve("testpack/dimensions/overworld.json");
        Files.createDirectories(dimension.getParent());
        Files.writeString(dimension, "aaaa", StandardCharsets.UTF_8);
        ServerConfigurator.PackFingerprint first =
                ServerConfigurator.resolvePackFingerprint(packsDir, "", "");
        assertEquals(PackFingerprints.computePackFingerprint(packsDir), first.content());
        assertNotEquals("", first.metadata());

        FileTime originalMtime = Files.getLastModifiedTime(dimension);
        Files.writeString(dimension, "bbbb", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(dimension, originalMtime);
        ServerConfigurator.PackFingerprint reused =
                ServerConfigurator.resolvePackFingerprint(packsDir, first.metadata(), first.content());

        assertEquals("Unchanged metadata must reuse the cached content fingerprint",
                first.content(), reused.content());

        Files.setLastModifiedTime(dimension, FileTime.fromMillis(originalMtime.toMillis() + 5000L));
        ServerConfigurator.PackFingerprint rehashed =
                ServerConfigurator.resolvePackFingerprint(packsDir, first.metadata(), first.content());

        assertNotEquals("Changed metadata must re-hash pack contents",
                first.content(), rehashed.content());
        assertEquals(PackFingerprints.computePackFingerprint(packsDir), rehashed.content());
    }

    @Test
    public void recoveryForcesAPostRecoveryContentFingerprint() throws Exception {
        File packsDir = tmp.newFolder("recovered-packs");
        Path dimension = packsDir.toPath().resolve("testpack/dimensions/overworld.json");
        Files.createDirectories(dimension.getParent());
        Files.writeString(dimension, "aaaa", StandardCharsets.UTF_8);
        ServerConfigurator.PackFingerprint cached =
                ServerConfigurator.resolvePackFingerprint(packsDir, "", "");
        FileTime originalMtime = Files.getLastModifiedTime(dimension);

        Files.writeString(dimension, "bbbb", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(dimension, originalMtime);

        ServerConfigurator.PackFingerprint reused =
                ServerConfigurator.resolvePostRecoveryPackFingerprint(
                        packsDir,
                        cached.metadata(),
                        cached.content(),
                        DatapackIngestService.ReapplyOutcome.success(false, false));
        ServerConfigurator.PackFingerprint recovered =
                ServerConfigurator.resolvePostRecoveryPackFingerprint(
                        packsDir,
                        cached.metadata(),
                        cached.content(),
                        DatapackIngestService.ReapplyOutcome.success(true, false));

        assertEquals(cached.content(), reused.content());
        assertNotEquals(cached.content(), recovered.content());
        assertEquals(PackFingerprints.computePackFingerprint(packsDir), recovered.content());
    }

    @Test
    public void fullInstallRequiresRestartWhenRecoveryOrRepairChangedFiles() {
        assertEquals(
                DatapackInstallResult.Status.UNCHANGED,
                ServerConfigurator.resultForUnchangedFingerprint(
                        true,
                        DatapackIngestService.ReapplyOutcome.success(false, false)).status());
        assertEquals(
                DatapackInstallResult.Status.RESTART_REQUIRED,
                ServerConfigurator.resultForUnchangedFingerprint(
                        true,
                        DatapackIngestService.ReapplyOutcome.success(true, false)).status());
        assertEquals(
                DatapackInstallResult.Status.RESTART_REQUIRED,
                ServerConfigurator.resultForUnchangedFingerprint(
                        true,
                        DatapackIngestService.ReapplyOutcome.success(false, true)).status());
        assertEquals(
                DatapackInstallResult.Status.READY,
                ServerConfigurator.resultForUnchangedFingerprint(
                        false,
                        DatapackIngestService.ReapplyOutcome.success(true, true)).status());
        assertEquals(
                DatapackInstallResult.Status.FAILED,
                ServerConfigurator.resultForUnchangedFingerprint(
                        true,
                        DatapackIngestService.ReapplyOutcome.failed(
                                new IOException("recovery failed"))).status());
    }

    @Test
    public void computePackMetadataDigestIgnoresGeneratedCodeWorkspaceFiles() throws Exception {
        File packsDir = tmp.newFolder("metadata-workspace-packs");
        Path dimension = packsDir.toPath().resolve("overworld/dimensions/overworld.json");
        Files.createDirectories(dimension.getParent());
        Files.writeString(dimension, "authored", StandardCharsets.UTF_8);
        String before = PackFingerprints.computePackMetadataDigest(packsDir);

        Files.writeString(packsDir.toPath().resolve("overworld/overworld.code-workspace"),
                "{\"folders\":[]}", StandardCharsets.UTF_8);

        assertEquals(before, PackFingerprints.computePackMetadataDigest(packsDir));
    }

    @Test
    public void computePackFingerprintRejectsSymbolicLinks() throws Exception {
        File packsDir = tmp.newFolder("unsafe-packs");
        Path pack = packsDir.toPath().resolve("testpack");
        Path outside = tmp.newFile("outside.json").toPath();
        Files.createDirectories(pack);
        Path link = pack.resolve("linked.json");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assume.assumeNoException(exception);
        }

        try {
            PackFingerprints.computePackFingerprint(packsDir);
            fail("Symbolic links must be rejected");
        } catch (UncheckedIOException expected) {
            assertTrue(expected.getMessage().contains("fingerprint"));
        }
    }

    @Test
    public void computePackFingerprintReadsSafeSymbolicPackRoots() throws Exception {
        File packsDir = tmp.newFolder("linked-root-packs");
        Path externalPack = tmp.newFolder("linked-pack").toPath();
        Path dimension = externalPack.resolve("dimensions/overworld.json");
        Files.createDirectories(dimension.getParent());
        Files.writeString(dimension, "first", StandardCharsets.UTF_8);
        Path link = packsDir.toPath().resolve("overworld");
        try {
            Files.createSymbolicLink(link, externalPack);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assume.assumeNoException(exception);
        }
        String before = PackFingerprints.computePackFingerprint(packsDir);

        Files.writeString(dimension, "other", StandardCharsets.UTF_8);

        assertNotEquals(before, PackFingerprints.computePackFingerprint(packsDir));
    }

    @Test
    public void computePackFingerprintReadsSafeSymbolicWorkspaceRoots() throws Exception {
        Path workspace = tmp.newFolder("pack-workspace").toPath();
        Path externalPack = tmp.newFolder("workspace-linked-pack").toPath();
        Path dimension = externalPack.resolve("dimensions/overworld.json");
        Files.createDirectories(dimension.getParent());
        Files.writeString(dimension, "first", StandardCharsets.UTF_8);
        Path packLink = workspace.resolve("overworld");
        Path workspaceLink = tmp.getRoot().toPath().resolve("packs-link");
        try {
            Files.createSymbolicLink(packLink, externalPack);
            Files.createSymbolicLink(workspaceLink, workspace);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assume.assumeNoException(exception);
        }
        String before = PackFingerprints.computePackFingerprint(workspaceLink.toFile());
        assertEquals(PackFingerprints.computePackFingerprint(workspace.toFile()), before);

        Files.writeString(dimension, "other", StandardCharsets.UTF_8);

        assertNotEquals(before, PackFingerprints.computePackFingerprint(workspaceLink.toFile()));
    }

    @Test
    public void computePackFingerprintRejectsDanglingSymbolicWorkspaceRoots() throws Exception {
        Path workspaceLink = tmp.getRoot().toPath().resolve("dangling-packs-link");
        try {
            Files.createSymbolicLink(workspaceLink, tmp.getRoot().toPath().resolve("missing-workspace"));
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assume.assumeNoException(exception);
        }

        try {
            PackFingerprints.computePackFingerprint(workspaceLink.toFile());
            fail("Dangling symbolic workspace roots must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("missing or unsafe"));
        }
    }

    @Test
    public void loadedRuntimeReuseRequiresAnExactPinnedCompilerInputFingerprint() {
        assertTrue(ServerConfigurator.reusableRuntimeFingerprint("abc", "abc"));
        assertFalse(ServerConfigurator.reusableRuntimeFingerprint("abc", "def"));
        assertFalse(ServerConfigurator.reusableRuntimeFingerprint("", ""));
        assertFalse(ServerConfigurator.reusableRuntimeFingerprint(null, "abc"));
    }

    @Test
    public void loadedRegistryAllowsUnrelatedEntriesButRequiresExactRequestedContent() {
        Map<String, String> loaded = Map.of(
                "dimension_type/iris:overworld", "dimension-a",
                "worldgen/biome/overworld:forest", "biome-a",
                "dimension/iris:ow", "level-stem-a");

        assertTrue(ServerConfigurator.loadedRegistrySatisfies(
                loaded,
                Map.of(
                        "dimension_type/iris:overworld", "dimension-a",
                        "worldgen/biome/overworld:forest", "biome-a")));
        assertFalse(ServerConfigurator.loadedRegistrySatisfies(
                loaded,
                Map.of("dimension_type/iris:overworld", "dimension-b")));
        assertFalse(ServerConfigurator.loadedRegistrySatisfies(
                loaded,
                Map.of("worldgen/biome/overworld:new", "biome-new")));
        assertFalse(ServerConfigurator.runtimeRequiresRegistryRestart(
                loaded,
                Map.of(
                        "dimension_type/iris:overworld", "dimension-a",
                        "worldgen/biome/overworld:forest", "biome-a")));
        assertTrue(ServerConfigurator.runtimeRequiresRegistryRestart(
                loaded,
                Map.of("dimension_type/iris:overworld", "dimension-b")));
        assertFalse(ServerConfigurator.runtimeRequiresRegistryRestart(loaded, Map.of()));
    }

    @Test
    public void restoredCompilerInputFingerprintRequiresReadyNonRestartingRuntime() throws Exception {
        Field ready = ServerConfigurator.class.getDeclaredField("loadedDatapackRuntimeReady");
        Field fingerprint = ServerConfigurator.class.getDeclaredField(
                "loadedDatapackCompilerInputFingerprint");
        Field restartRequired = ServerConfigurator.class.getDeclaredField("loadedDatapackRestartRequired");
        ready.setAccessible(true);
        fingerprint.setAccessible(true);
        restartRequired.setAccessible(true);
        boolean previousReady = ready.getBoolean(null);
        String previousFingerprint = (String) fingerprint.get(null);
        boolean previousRestartRequired = restartRequired.getBoolean(null);

        try {
            ready.setBoolean(null, true);
            fingerprint.set(null, "restored-fingerprint");
            restartRequired.setBoolean(null, false);
            assertEquals("restored-fingerprint", ServerConfigurator.restoredCompilerInputFingerprint());

            restartRequired.setBoolean(null, true);
            assertEquals("", ServerConfigurator.restoredCompilerInputFingerprint());

            restartRequired.setBoolean(null, false);
            ready.setBoolean(null, false);
            assertEquals("", ServerConfigurator.restoredCompilerInputFingerprint());
        } finally {
            ready.setBoolean(null, previousReady);
            fingerprint.set(null, previousFingerprint);
            restartRequired.setBoolean(null, previousRestartRequired);
        }
    }

    @Test
    public void externalDatapackMutationInvalidatesReadinessAndRetainsComparisonPin() throws Exception {
        Field ready = ServerConfigurator.class.getDeclaredField("loadedDatapackRuntimeReady");
        Field fingerprint = ServerConfigurator.class.getDeclaredField(
                "loadedDatapackCompilerInputFingerprint");
        ready.setAccessible(true);
        fingerprint.setAccessible(true);
        ready.setBoolean(null, true);
        fingerprint.set(null, "abc");

        ServerConfigurator.invalidateLoadedDatapackRuntime();

        assertFalse(ready.getBoolean(null));
        assertEquals("abc", fingerprint.get(null));
    }
}
