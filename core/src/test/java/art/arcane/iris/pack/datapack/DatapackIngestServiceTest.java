package art.arcane.iris.pack.datapack;

import art.arcane.iris.structure.authoring.StructureBackend;
import art.arcane.iris.structure.authoring.StructureCapability;
import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureResourceBundle;
import art.arcane.iris.structure.authoring.StructureRecoveryResult;
import art.arcane.iris.structure.authoring.StructureSource;
import art.arcane.iris.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.structure.authoring.StructureWriteMode;
import art.arcane.iris.structure.authoring.StructureWriteResult;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.DurabilityMode;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.io.IO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Server;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DatapackIngestServiceTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    private interface PaperLikeServer extends Server {
        String getMinecraftVersion();
    }

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void serverVersionUsesTheRuntimeMinecraftReleaseInsteadOfTheLeafBuildToken() {
        PaperLikeServer server = mock(PaperLikeServer.class);
        doReturn("26.2").when(server).getMinecraftVersion();
        doReturn("26.2.build.33").when(server).getBukkitVersion();

        assertEquals("26.2", DatapackIngestService.serverMcVersion(server));
    }

    @Test
    public void serverVersionPreservesLegacyThreePartMinecraftReleases() {
        Server server = mock(Server.class);
        doReturn("1.21.4-R0.1-SNAPSHOT").when(server).getBukkitVersion();

        assertEquals("1.21.4", DatapackIngestService.serverMcVersion(server));
    }

    @Test
    public void windowsLongPathAliasAcceptsTheSameVolumeSerialAndRoot() throws Exception {
        FileStore shortPathStore = mock(FileStore.class);
        FileStore longPathStore = mock(FileStore.class);
        when(shortPathStore.getAttribute("volume:vsn")).thenReturn(41234L);
        when(longPathStore.getAttribute("volume:vsn")).thenReturn(41234L);

        assertTrue(DatapackScratchRecovery.sameWindowsVolume(
                shortPathStore, "C:\\", longPathStore, "c:\\"));
    }

    @Test
    public void windowsLongPathAliasRejectsDifferentRootsAndVolumeSerials() throws Exception {
        FileStore firstStore = mock(FileStore.class);
        FileStore secondStore = mock(FileStore.class);
        when(firstStore.getAttribute("volume:vsn")).thenReturn(1L);
        when(secondStore.getAttribute("volume:vsn")).thenReturn(2L);

        assertFalse(DatapackScratchRecovery.sameWindowsVolume(
                firstStore, "C:\\", secondStore, "C:\\"));

        when(secondStore.getAttribute("volume:vsn")).thenReturn(1L);
        assertFalse(DatapackScratchRecovery.sameWindowsVolume(
                firstStore, "C:\\", secondStore, "D:\\"));
    }

    @Test
    public void scratchDirectoryRejectsJunctionLikeOtherAttributes() {
        BasicFileAttributes attributes = mock(BasicFileAttributes.class);
        when(attributes.isDirectory()).thenReturn(true);
        when(attributes.isOther()).thenReturn(true);

        assertFalse(DatapackScratchRecovery.isSupportedScratchDirectory(attributes));

        when(attributes.isOther()).thenReturn(false);
        assertTrue(DatapackScratchRecovery.isSupportedScratchDirectory(attributes));
    }

    @Test
    public void startupValidationCacheRequiresEveryInputAndLocalFingerprint() {
        StartupValidationCache cache = new StartupValidationCache();
        cache.schemaVersion = 1;
        cache.minecraftVersion = "26.2";
        cache.irisVersion = 4000;
        cache.autoIngest = true;
        cache.stripOverrides = false;
        cache.urls = List.of("https://modrinth.com/datapack/example");
        cache.localFingerprint = "fingerprint";

        assertTrue(DatapackStartupValidation.startupValidationCacheMatches(
                cache,
                "26.2",
                4000,
                true,
                false,
                List.of("https://modrinth.com/datapack/example"),
                "fingerprint"));
        assertFalse(DatapackStartupValidation.startupValidationCacheMatches(
                cache,
                "26.3",
                4000,
                true,
                false,
                cache.urls,
                "fingerprint"));
        assertFalse(DatapackStartupValidation.startupValidationCacheMatches(
                cache,
                "26.2",
                4000,
                true,
                true,
                cache.urls,
                "fingerprint"));
        assertFalse(DatapackStartupValidation.startupValidationCacheMatches(
                cache,
                "26.2",
                4000,
                true,
                false,
                List.of("https://modrinth.com/datapack/changed"),
                "fingerprint"));
        assertFalse(DatapackStartupValidation.startupValidationCacheMatches(
                cache,
                "26.2",
                4000,
                true,
                false,
                cache.urls,
                "changed"));
    }

    @Test
    public void startupValidationFingerprintChangesWithStagedContent() throws Exception {
        File root = temporaryFolder.newFolder("startup-validation-fingerprint");
        File staged = new File(root, "staging/managed");
        assertTrue(staged.mkdirs());
        Path content = new File(staged, "value.txt").toPath();
        Files.writeString(content, "alpha", StandardCharsets.UTF_8);
        KList<File> worldFolders = new KList<>();
        String before = DatapackStartupValidation.startupValidationFingerprint(root, worldFolders);

        Files.writeString(content, "bravo", StandardCharsets.UTF_8);
        String after = DatapackStartupValidation.startupValidationFingerprint(root, worldFolders);

        assertFalse(before.equals(after));
    }

    @Test
    public void authorizedStartupMaintenanceRefreshesOnlyTheLocalFingerprint() throws Exception {
        File root = temporaryFolder.newFolder("startup-validation-maintenance");
        File staging = new File(root, "staging/managed");
        assertTrue(staging.mkdirs());
        Path content = new File(staging, "value.txt").toPath();
        Files.writeString(content, "before", StandardCharsets.UTF_8);
        KList<File> worldFolders = new KList<>();

        StartupValidationCache validated = new StartupValidationCache();
        validated.schemaVersion = 1;
        validated.minecraftVersion = "26.2";
        validated.irisVersion = 4000;
        validated.autoIngest = true;
        validated.stripOverrides = false;
        validated.urls = List.of("https://modrinth.com/datapack/example");
        validated.localFingerprint = DatapackStartupValidation.startupValidationFingerprint(root, worldFolders);

        Files.writeString(content, "after", StandardCharsets.UTF_8);
        assertFalse(DatapackStartupValidation.startupValidationCacheMatches(
                validated,
                "26.2",
                4000,
                true,
                false,
                validated.urls,
                DatapackStartupValidation.startupValidationFingerprint(root, worldFolders)));

        StartupValidationCache refreshed =
                DatapackStartupValidation.refreshStartupValidationCache(validated, root, worldFolders);

        assertTrue(DatapackStartupValidation.startupValidationCacheMatches(
                refreshed,
                "26.2",
                4000,
                true,
                false,
                validated.urls,
                DatapackStartupValidation.startupValidationFingerprint(root, worldFolders)));
        assertEquals(validated.urls, refreshed.urls);
    }

    @Test
    public void startupMaintenanceDoesNotAuthorizeChangedValidationInputs() {
        StartupValidationCache validated = new StartupValidationCache();
        validated.schemaVersion = 1;
        validated.minecraftVersion = "26.2";
        validated.irisVersion = 4000;
        validated.autoIngest = true;
        validated.stripOverrides = false;
        validated.urls = List.of("https://modrinth.com/datapack/example");

        assertTrue(DatapackStartupValidation.startupValidationContextMatches(
                validated, "26.2", 4000, true, false, validated.urls));
        assertFalse(DatapackStartupValidation.startupValidationContextMatches(
                validated, "26.2", 4000, true, false,
                List.of("https://modrinth.com/datapack/changed")));
        assertFalse(DatapackStartupValidation.startupValidationContextMatches(
                validated, "26.2", 4000, true, true, validated.urls));
    }

    @Test
    public void packMetadataMustContainAValidPackContract() throws Exception {
        File valid = temporaryFolder.newFolder("valid");
        Files.writeString(new File(valid, "pack.mcmeta").toPath(), """
                {"pack":{"description":"test","pack_format":88}}
                """, StandardCharsets.UTF_8);

        DatapackPackMetadata.validatePackMetadata(valid);

        File invalid = temporaryFolder.newFolder("invalid");
        Files.writeString(new File(invalid, "pack.mcmeta").toPath(), "{}", StandardCharsets.UTF_8);
        try {
            DatapackPackMetadata.validatePackMetadata(invalid);
            fail("Expected invalid metadata to be rejected");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("pack object"));
        }
    }

    @Test
    public void downloadFollowsRelativeRedirects() throws Exception {
        byte[] archive = "archive".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", "files/pack.zip");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/files/pack.zip", exchange -> {
            exchange.sendResponseHeaders(200, archive.length);
            exchange.getResponseBody().write(archive);
            exchange.close();
        });
        server.start();
        try {
            File destination = new File(temporaryFolder.newFolder("download"), "pack.zip");
            DownloadResult result = DatapackArchive.download(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/start",
                    destination,
                    null,
                    null
            );

            assertFalse(result.notModified());
            assertEquals("archive", Files.readString(destination.toPath(), StandardCharsets.UTF_8));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void downloadUsesConditionalValidators() throws Exception {
        AtomicBoolean conditional = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pack.zip", exchange -> {
            conditional.set("\"v1\"".equals(exchange.getRequestHeaders().getFirst("If-None-Match"))
                    && "Wed, 21 Oct 2015 07:28:00 GMT".equals(exchange.getRequestHeaders().getFirst("If-Modified-Since")));
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
        });
        server.start();
        try {
            File destination = new File(temporaryFolder.newFolder("conditional"), "pack.zip");
            DownloadResult result = DatapackArchive.download(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/pack.zip",
                    destination,
                    "\"v1\"",
                    "Wed, 21 Oct 2015 07:28:00 GMT"
            );

            assertTrue(result.notModified());
            assertTrue(conditional.get());
            assertFalse(destination.exists());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void downloadCopiesLocalFileUrl() throws Exception {
        Path source = temporaryFolder.newFile("local source.zip").toPath();
        Files.writeString(source, "local-archive", StandardCharsets.UTF_8);
        File destination = new File(temporaryFolder.newFolder("local-download"), "pack.zip");

        DownloadResult result = DatapackArchive.download(
                source.toUri().toASCIIString(),
                destination,
                null,
                null
        );

        assertFalse(result.notModified());
        assertEquals("local-archive", Files.readString(destination.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void failedLocalDownloadPreservesExistingDestination() throws Exception {
        Path missing = temporaryFolder.getRoot().toPath().resolve("missing.zip");
        File destination = new File(temporaryFolder.newFolder("local-download-existing"), "pack.zip");
        Files.writeString(destination.toPath(), "existing", StandardCharsets.UTF_8);

        try {
            DatapackArchive.download(
                    missing.toUri().toASCIIString(),
                    destination,
                    null,
                    null
            );
            fail("Expected missing local source rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("regular non-symbolic-link file"));
        }

        assertEquals("existing", Files.readString(destination.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void localDropFolderDiscoversOnlyTopLevelZipFilesInStableOrder() throws Exception {
        File imports = temporaryFolder.newFolder("local-imports");
        Path first = new File(imports, "Alpha Pack.ZIP").toPath();
        Path second = new File(imports, "bravo.zip").toPath();
        Files.writeString(first, "alpha", StandardCharsets.UTF_8);
        Files.writeString(second, "bravo", StandardCharsets.UTF_8);
        Files.writeString(new File(imports, "ignored.txt").toPath(), "ignored", StandardCharsets.UTF_8);
        Path nested = new File(imports, "nested/hidden.zip").toPath();
        Files.createDirectories(nested.getParent());
        Files.writeString(nested, "nested", StandardCharsets.UTF_8);

        List<String> discovered = DatapackImportSources.discoverLocalDatapackImports(imports);

        assertEquals(List.of(
                first.toAbsolutePath().normalize().toUri().toASCIIString(),
                second.toAbsolutePath().normalize().toUri().toASCIIString()
        ), discovered);
    }

    @Test
    public void configuredFileSourceNormalizesToTheDiscoveredArchiveUri() throws Exception {
        Path source = temporaryFolder.newFile("normalized-local.zip").toPath();
        String discovered = source.toAbsolutePath().normalize().toUri().toASCIIString();
        String alternate = discovered.replace("file:///", "file:/");

        assertEquals(discovered, DatapackImportSources.normalizeConfiguredSource(alternate));
        assertEquals(
                Set.of("https://example.test/dimension.zip", discovered),
                DatapackImportSources.mergeConfiguredImports(
                        List.of("https://example.test/dimension.zip", alternate),
                        List.of(discovered)));
    }

    @Test
    public void startupValidationFingerprintChangesWithLocalSourceBytes() throws Exception {
        File root = temporaryFolder.newFolder("startup-local-source-fingerprint");
        Path source = temporaryFolder.newFile("startup-local.zip").toPath();
        KList<File> worldFolders = new KList<>();
        String sourceUrl = source.toUri().toASCIIString();
        Files.writeString(source, "alpha", StandardCharsets.UTF_8);
        String before = DatapackStartupValidation.startupValidationFingerprint(
                root, worldFolders, List.of(sourceUrl));

        FileTime originalTime = Files.getLastModifiedTime(source);
        Files.writeString(source, "bravo", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(source, originalTime);
        String after = DatapackStartupValidation.startupValidationFingerprint(
                root, worldFolders, List.of(sourceUrl));

        assertFalse(before.equals(after));
    }

    @Test
    public void removalRequiresMatchingIrisOwnership() throws Exception {
        File unmanaged = datapackDirectory("unmanaged");
        try {
            DatapackOwnership.deleteOwnedDirectory(unmanaged, "unmanaged");
            fail("Expected unmanaged datapack removal to be rejected");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("ownership marker"));
        }
        assertTrue(unmanaged.isDirectory());

        File managed = datapackDirectory("managed");
        DatapackIngestService.Entry entry = new DatapackIngestService.Entry();
        entry.id = "managed";
        entry.url = "https://example.test/managed.zip";
        entry.versionId = "version";
        entry.versionNumber = "1";
        entry.sha1 = "hash";
        DatapackOwnership.writeOwnership(managed, entry);

        assertTrue(DatapackOwnership.deleteOwnedDirectory(managed, "managed"));
        assertFalse(managed.exists());
    }

    @Test
    public void removalRefusesSymbolicLinkTargets() throws Exception {
        File outside = datapackDirectory("outside");
        File link = new File(temporaryFolder.getRoot(), "linked-pack");
        Files.createSymbolicLink(link.toPath(), outside.toPath());

        try {
            DatapackOwnership.deleteOwnedDirectory(link, "outside");
            fail("Expected symbolic-link removal to be rejected");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("symbolic-link"));
        }
        assertTrue(new File(outside, "pack.mcmeta").isFile());
    }

    @Test
    public void removalRefusesModifiedManagedDirectories() throws Exception {
        File managed = datapackDirectory("modified-managed");
        DatapackIngestService.Entry entry = new DatapackIngestService.Entry();
        entry.id = "modified-managed";
        entry.url = "https://example.test/modified.zip";
        DatapackOwnership.writeOwnership(managed, entry);
        Files.writeString(new File(managed, "user-edit.txt").toPath(), "preserve", StandardCharsets.UTF_8);

        try {
            DatapackOwnership.deleteOwnedDirectory(managed, entry.id);
            fail("Expected modified managed datapack removal to be rejected");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("modified or corrupt"));
        }
        assertEquals("preserve", Files.readString(new File(managed, "user-edit.txt").toPath()));
    }

    @Test
    public void malformedRemovalIdCannotAliasARealManagedId() throws Exception {
        File root = temporaryFolder.newFolder("removal-invalid-id-root");
        Files.writeString(new File(root, "manifest.json").toPath(), """
                {"entries":[{"url":"https://example.test/pack.zip","id":"datapack"}]}
                """, StandardCharsets.UTF_8);
        DatapackIngestService.Entry entry = new DatapackIngestService.Entry();
        entry.id = "datapack";
        entry.url = "https://example.test/pack.zip";
        File staging = new File(new File(root, "staging"), entry.id);
        writeManagedDatapack(staging, entry);

        assertFalse(DatapackRemoval.removeLocked(null, "!!!", root, List.of()));
        assertTrue(staging.isDirectory());
        assertTrue(Files.readString(new File(root, "manifest.json").toPath()).contains("datapack"));
    }

    @Test
    public void malformedManifestIdCannotAliasARealManagedDirectory() throws Exception {
        File root = temporaryFolder.newFolder("malformed-manifest-id-root");
        Files.writeString(new File(root, "manifest.json").toPath(), """
                {"entries":[{"url":"https://example.test/managed.zip","id":"../managed"}]}
                """, StandardCharsets.UTF_8);
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        File staging = new File(new File(root, "staging"), entry.id);
        writeManagedDatapack(staging, entry);

        assertFalse(DatapackRemoval.removeLocked(null, entry.id, root, List.of()));

        assertTrue(staging.isDirectory());
    }

    @Test
    public void ownershipMarkersRejectUnsafeManagedIds() throws Exception {
        File managed = datapackDirectory("unsafe-ownership-id");
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        entry.id = "../managed";

        try {
            DatapackOwnership.writeOwnership(managed, entry);
            fail("Expected unsafe ownership id to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("ownership identity"));
        }
        assertFalse(new File(managed, ".iris-managed.json").exists());
    }

    @Test
    public void removalRefusesSymbolicLinkDatapacksContainer() throws Exception {
        File root = temporaryFolder.newFolder("removal-link-container-root");
        Files.writeString(new File(root, "manifest.json").toPath(), """
                {"entries":[{"url":"https://example.test/managed.zip","id":"managed"}]}
                """, StandardCharsets.UTF_8);
        DatapackIngestService.Entry entry = new DatapackIngestService.Entry();
        entry.id = "managed";
        entry.url = "https://example.test/managed.zip";
        File outside = temporaryFolder.newFolder("removal-link-container-outside");
        File outsideTarget = new File(outside, entry.id);
        writeManagedDatapack(outsideTarget, entry);
        File linkedContainer = new File(temporaryFolder.getRoot(), "linked-datapacks-container");
        Files.createSymbolicLink(linkedContainer.toPath(), outside.toPath());

        assertFalse(DatapackRemoval.removeLocked(null, entry.id, root, List.of(linkedContainer)));
        assertTrue(outsideTarget.isDirectory());
    }

    @Test
    public void removalPreflightFailurePreservesEditableImportInventoryAndManifest() throws Exception {
        File root = temporaryFolder.newFolder("removal-preflight-root");
        File manifest = new File(root, "manifest.json");
        String manifestJson = """
                {"entries":[{"url":"https://example.test/managed.zip","id":"managed",
                "importedTargets":{"/missing/editable-pack":"revision"},
                "importedBundles":{"/missing/editable-pack":{"iris:owned":"example:owned"}}}]}
                """;
        Files.writeString(manifest.toPath(), manifestJson, StandardCharsets.UTF_8);

        File worldDatapacks = temporaryFolder.newFolder("removal-preflight-world");
        File unmanagedTarget = new File(worldDatapacks, "managed");
        assertTrue(unmanagedTarget.mkdirs());
        Files.writeString(new File(unmanagedTarget, "pack.mcmeta").toPath(), """
                {"pack":{"description":"test","pack_format":88}}
                """, StandardCharsets.UTF_8);
        KList<File> worlds = new KList<>();
        worlds.add(worldDatapacks);

        assertFalse(DatapackRemoval.removeLocked(null, "managed", root, worlds));
        assertEquals(manifestJson, Files.readString(manifest.toPath(), StandardCharsets.UTF_8));
        assertTrue(unmanagedTarget.isDirectory());
    }

    @Test
    public void laterDirectoryMoveFailureRestoresEveryEditableBundleAndLeavesManifestUntouched() throws Exception {
        File root = temporaryFolder.newFolder("removal-transaction-root");
        File editablePack = temporaryFolder.newFolder("removal-transaction-editable");
        StructureKey alphaKey = StructureKey.parse("iris:alpha");
        StructureKey zetaKey = StructureKey.parse("iris:zeta");
        StructureKey alphaSource = StructureKey.parse("example:alpha");
        StructureKey zetaSource = StructureKey.parse("example:zeta");
        StructureTransactionWriter writer = new StructureTransactionWriter(editablePack.toPath());
        StructureWriteResult alphaWrite = writer.writeManagedDatapack(
                importedBundle(alphaKey, alphaSource, "alpha"),
                StructureWriteMode.ADD_ONLY
        );
        StructureWriteResult zetaWrite = writer.writeManagedDatapack(
                importedBundle(zetaKey, zetaSource, "zeta"),
                StructureWriteMode.ADD_ONLY
        );
        assertEquals(StructureWriteResult.Status.ADDED, alphaWrite.status());
        assertEquals(StructureWriteResult.Status.ADDED, zetaWrite.status());

        String editablePath = editablePack.getAbsolutePath().replace("\\", "\\\\").replace("\"", "\\\"");
        String manifestJson = """
                {"entries":[{"url":"https://example.test/managed.zip","id":"managed",
                "importedTargets":{"%s":"revision"},
                "importedBundles":{"%s":{"iris:alpha":"example:alpha","iris:zeta":"example:zeta"}}}]}
                """.formatted(editablePath, editablePath);
        File manifest = new File(root, "manifest.json");
        Files.writeString(manifest.toPath(), manifestJson, StandardCharsets.UTF_8);

        DatapackIngestService.Entry entry = new DatapackIngestService.Entry();
        entry.id = "managed";
        entry.url = "https://example.test/managed.zip";
        File stagingTarget = new File(new File(root, "staging"), entry.id);
        writeManagedDatapack(stagingTarget, entry);
        File worldDatapacks = temporaryFolder.newFolder("removal-transaction-world");
        File worldTarget = new File(worldDatapacks, entry.id);
        writeManagedDatapack(worldTarget, entry);

        File blockedBackupRoot = new File(temporaryFolder.getRoot(), ".iris-datapack-remove");
        Files.writeString(blockedBackupRoot.toPath(), "block-directory-creation", StandardCharsets.UTF_8);
        KList<File> worlds = new KList<>();
        worlds.add(worldDatapacks);

        assertFalse(DatapackRemoval.removeLocked(null, entry.id, root, worlds));
        assertEquals(manifestJson, Files.readString(manifest.toPath(), StandardCharsets.UTF_8));
        assertEquals("alpha", Files.readString(new File(editablePack, "objects/alpha.iob").toPath()));
        assertEquals("zeta", Files.readString(new File(editablePack, "objects/zeta.iob").toPath()));
        assertTrue(Files.exists(writer.ownershipManifestPath(alphaKey)));
        assertTrue(Files.exists(writer.ownershipManifestPath(zetaKey)));
        assertTrue(stagingTarget.isDirectory());
        assertTrue(worldTarget.isDirectory());
    }

    @Test
    public void successfulRemovalCommitsEditableBundlesDirectoriesAndManifestTogether() throws Exception {
        File root = temporaryFolder.newFolder("removal-commit-root");
        File editablePack = temporaryFolder.newFolder("removal-commit-editable");
        StructureKey targetKey = StructureKey.parse("iris:owned");
        StructureKey sourceKey = StructureKey.parse("example:owned");
        StructureTransactionWriter writer = new StructureTransactionWriter(editablePack.toPath());
        StructureWriteResult write = writer.writeManagedDatapack(
                importedBundle(targetKey, sourceKey, "owned"),
                StructureWriteMode.ADD_ONLY
        );
        assertEquals(StructureWriteResult.Status.ADDED, write.status());

        String editablePath = editablePack.getAbsolutePath().replace("\\", "\\\\").replace("\"", "\\\"");
        File manifest = new File(root, "manifest.json");
        Files.writeString(manifest.toPath(), """
                {"entries":[{"url":"https://example.test/managed.zip","id":"managed",
                "importedTargets":{"%s":"revision"},
                "importedBundles":{"%s":{"iris:owned":"example:owned"}}}]}
                """.formatted(editablePath, editablePath), StandardCharsets.UTF_8);

        DatapackIngestService.Entry entry = new DatapackIngestService.Entry();
        entry.id = "managed";
        entry.url = "https://example.test/managed.zip";
        File stagingTarget = new File(new File(root, "staging"), entry.id);
        writeManagedDatapack(stagingTarget, entry);
        File worldDatapacks = temporaryFolder.newFolder("removal-commit-world");
        File worldTarget = new File(worldDatapacks, entry.id);
        writeManagedDatapack(worldTarget, entry);
        KList<File> worlds = new KList<>();
        worlds.add(worldDatapacks);

        assertTrue(DatapackRemoval.removeLocked(null, entry.id, root, worlds));
        assertFalse(Files.exists(new File(editablePack, "objects/owned.iob").toPath()));
        assertFalse(Files.exists(writer.ownershipManifestPath(targetKey)));
        assertFalse(stagingTarget.exists());
        assertFalse(worldTarget.exists());
        assertFalse(Files.readString(manifest.toPath(), StandardCharsets.UTF_8).contains("managed"));
    }

    @Test
    public void removalPreservesAnOrdinaryEditableBundleWithMatchingInventory() throws Exception {
        File root = temporaryFolder.newFolder("ordinary-editable-removal-root");
        File editablePack = temporaryFolder.newFolder("ordinary-editable-removal-pack");
        StructureKey targetKey = StructureKey.parse("iris:owned");
        StructureKey sourceKey = StructureKey.parse("example:owned");
        StructureTransactionWriter writer = new StructureTransactionWriter(editablePack.toPath());
        assertEquals(StructureWriteResult.Status.ADDED, writer.write(
                importedBundle(targetKey, sourceKey, "ordinary"),
                StructureWriteMode.ADD_ONLY
        ).status());
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        String targetId = editablePack.getAbsolutePath();
        entry.importedTargets.put(targetId, "revision");
        entry.importedBundles.put(targetId, Map.of(targetKey.value(), sourceKey.value()));
        writeManifest(root, entry);

        assertTrue(DatapackRemoval.removeLocked(null, entry.id, root, List.of()));

        assertEquals("ordinary", Files.readString(new File(editablePack, "objects/owned.iob").toPath()));
        assertTrue(Files.exists(writer.ownershipManifestPath(targetKey)));
    }

    @Test
    public void manifestEntryDoesNotAdoptUnmarkedStaging() throws Exception {
        File staging = datapackDirectory("legacy-staging");
        DatapackIngestService.Entry entry = new DatapackIngestService.Entry();
        entry.id = "legacy-staging";
        entry.url = "https://example.test/legacy.zip";

        assertFalse(DatapackStagingGuard.isUsableStaging(staging, entry));
        assertFalse(new File(staging, ".iris-managed.json").exists());
    }

    @Test
    public void committedEmptyManifestDoesNotAdoptAnUncommittedStagingCandidate() throws Exception {
        File root = temporaryFolder.newFolder("uncommitted-staging-root");
        writeManifest(root, null);
        DatapackIngestService.Entry candidate = entry("candidate", "v1", "1", "sha");
        File staging = new File(new File(root, "staging"), candidate.id);
        writeManagedDatapack(staging, candidate);

        assertFalse(DatapackRemoval.removeLocked(null, candidate.id, root, List.of()));

        assertTrue(staging.isDirectory());
        assertFalse(Files.readString(new File(root, "manifest.json").toPath(), StandardCharsets.UTF_8)
                .contains(candidate.id));
    }

    @Test
    public void quarantinedCorruptManifestCanRecoverVerifiedManagedStaging() throws Exception {
        File root = temporaryFolder.newFolder("corrupt-manifest-recovery-root");
        Files.writeString(new File(root, "manifest.json").toPath(), "{broken", StandardCharsets.UTF_8);
        DatapackIngestService.Entry candidate = entry("candidate", "v1", "1", "sha");
        File staging = new File(new File(root, "staging"), candidate.id);
        writeManagedDatapack(staging, candidate);

        assertTrue(DatapackRemoval.removeLocked(null, candidate.id, root, List.of()));

        assertFalse(staging.exists());
        assertFalse(Files.readString(new File(root, "manifest.json").toPath(), StandardCharsets.UTF_8)
                .contains(candidate.id));
    }

    @Test
    public void stagingOwnershipHashDetectsPostInstallMutation() throws Exception {
        File staging = datapackDirectory("mutated-staging");
        DatapackIngestService.Entry entry = new DatapackIngestService.Entry();
        entry.id = "mutated-staging";
        entry.url = "https://example.test/mutated.zip";
        entry.structureKeys = List.of("original:structure");
        entry.templateKeys = List.of("original:template");
        DatapackOwnership.writeOwnership(staging, entry);
        Files.writeString(new File(staging, "unexpected.txt").toPath(), "changed", StandardCharsets.UTF_8);

        assertFalse(DatapackStagingGuard.isUsableStaging(staging, entry));
        assertEquals(List.of("original:structure"), entry.structureKeys);
        assertEquals(List.of("original:template"), entry.templateKeys);
    }

    @Test
    public void finderMetadataDoesNotInvalidateManagedStaging() throws Exception {
        File staging = datapackDirectory("finder-metadata-staging");
        File nested = new File(staging, "data/example");
        assertTrue(nested.mkdirs());
        DatapackIngestService.Entry entry = entry("finder-metadata-staging", "v1", "1", "sha");
        DatapackOwnership.writeOwnership(staging, entry);
        File rootMetadata = new File(staging, ".DS_Store");
        File nestedMetadata = new File(nested, ".DS_Store");
        Files.writeString(rootMetadata.toPath(), "finder", StandardCharsets.UTF_8);
        Files.writeString(nestedMetadata.toPath(), "finder", StandardCharsets.UTF_8);

        assertTrue(DatapackStagingGuard.isUsableStaging(staging, entry));
        assertFalse(rootMetadata.exists());
        assertFalse(nestedMetadata.exists());
    }

    @Test
    public void finderMetadataDirectoryCannotBypassManagedHashing() throws Exception {
        File staging = datapackDirectory("finder-metadata-directory");
        assertTrue(new File(staging, ".DS_Store").mkdir());
        DatapackIngestService.Entry entry = entry("finder-metadata-directory", "v1", "1", "sha");

        try {
            DatapackOwnership.writeOwnership(staging, entry);
            fail("Expected suspicious Finder metadata to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Suspicious Finder metadata"));
        }

        assertFalse(new File(staging, ".iris-managed.json").exists());
    }

    @Test
    public void nestedOwnershipNamedResourceRemainsInsideTheManagedHash() throws Exception {
        File staging = datapackDirectory("nested-ownership-resource");
        File nestedMarker = new File(
                staging, "data/test/worldgen/structure/.iris-managed.json");
        assertTrue(nestedMarker.getParentFile().mkdirs());
        Files.writeString(nestedMarker.toPath(), "original", StandardCharsets.UTF_8);
        DatapackIngestService.Entry entry = entry("nested-ownership-resource", "v1", "1", "sha");
        DatapackOwnership.writeOwnership(staging, entry);

        assertTrue(DatapackStagingGuard.isUsableStaging(staging, entry));
        Files.writeString(nestedMarker.toPath(), "changed", StandardCharsets.UTF_8);

        assertFalse(DatapackStagingGuard.isUsableStaging(staging, entry));
        try {
            DatapackOwnership.deleteOwnedDirectory(staging, entry.id);
            fail("Expected nested managed resource mutation to block deletion");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("modified or corrupt"));
        }
        assertEquals("changed", Files.readString(nestedMarker.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void ownershipHashFramesFileBoundaries() throws Exception {
        File compact = datapackDirectory("hash-boundary-compact");
        File expanded = datapackDirectory("hash-boundary-expanded");
        Files.write(new File(compact, "a").toPath(), new byte[]{'x', (byte) 0xff, 'b', 0, 'y'});
        Files.write(new File(expanded, "a").toPath(), new byte[]{'x'});
        Files.write(new File(expanded, "b").toPath(), new byte[]{'y'});
        DatapackIngestService.Entry compactEntry = entry("compact", "v1", "1", "sha");
        DatapackIngestService.Entry expandedEntry = entry("expanded", "v1", "1", "sha");

        DatapackOwnership.writeOwnership(compact, compactEntry);
        DatapackOwnership.writeOwnership(expanded, expandedEntry);

        assertFalse(ownershipHash(compact).equals(ownershipHash(expanded)));
    }

    @Test
    public void ownershipHashIncludesEmptyDirectories() throws Exception {
        File managed = datapackDirectory("hash-empty-directory");
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        DatapackOwnership.writeOwnership(managed, entry);
        assertTrue(new File(managed, "user-directory").mkdir());

        try {
            DatapackOwnership.deleteOwnedDirectory(managed, entry.id);
            fail("Expected an added empty directory to invalidate ownership");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("modified or corrupt"));
        }
        assertTrue(managed.isDirectory());
    }

    @Test
    public void ownershipDirectoryHashRetainsGoldenFramingAndExclusions() throws Exception {
        File managed = temporaryFolder.newFolder("directory-hash-golden");
        Path data = managed.toPath().resolve("data");
        Path example = data.resolve("example");
        Files.createDirectories(example);
        Files.createDirectory(managed.toPath().resolve("empty"));
        Files.write(example.resolve("value.bin"), new byte[]{0, 1, 2, 3, (byte) 0xff});
        Files.writeString(
                managed.toPath().resolve("pack.mcmeta"),
                "{\"pack\":{\"description\":\"golden\",\"pack_format\":88}}",
                StandardCharsets.UTF_8);
        Files.writeString(managed.toPath().resolve("z.txt"), "Iris\n", StandardCharsets.UTF_8);
        Files.writeString(
                managed.toPath().resolve(".iris-managed.json"),
                "ignored ownership marker",
                StandardCharsets.UTF_8);
        Files.writeString(managed.toPath().resolve(".DS_Store"), "ignored root metadata");
        Files.writeString(data.resolve(".DS_Store"), "ignored nested metadata");
        DatapackIngestService.Entry entry = entry("golden", "v1", "1", "sha");

        DatapackOwnership.writeOwnership(managed, entry);

        String expected = "aa62ee4ed00f0393e637411686082f253ec65ff788839b12c30fc175e5b501fb";
        assertEquals(expected, ownershipHash(managed));

        Files.writeString(
                managed.toPath().resolve(".iris-managed.json"),
                "different ignored ownership marker",
                StandardCharsets.UTF_8);
        Files.writeString(managed.toPath().resolve(".DS_Store"), "different root metadata");
        Files.writeString(data.resolve(".DS_Store"), "different nested metadata");
        DatapackOwnership.writeOwnership(managed, entry);

        assertEquals(expected, ownershipHash(managed));
    }

    @Test
    public void failedUpdateStagingCannotBeAdoptedByTheCommittedManifest() throws Exception {
        File staging = datapackDirectory("candidate-staging");
        DatapackIngestService.Entry candidate = new DatapackIngestService.Entry();
        candidate.id = "candidate-staging";
        candidate.url = "https://example.test/candidate.zip";
        candidate.versionId = "v2";
        candidate.versionNumber = "2";
        candidate.sha1 = "new";
        candidate.structureKeys = List.of("candidate:new");
        DatapackOwnership.writeOwnership(staging, candidate);

        DatapackIngestService.Entry committed = new DatapackIngestService.Entry();
        committed.id = candidate.id;
        committed.url = candidate.url;
        committed.versionId = "v1";
        committed.versionNumber = "1";
        committed.sha1 = "old";
        committed.structureKeys = new ArrayList<>(List.of("committed:old"));

        assertFalse(DatapackStagingGuard.isUsableStaging(staging, committed));
        assertEquals(List.of("committed:old"), committed.structureKeys);
        assertTrue(Files.readString(new File(staging, ".iris-managed.json").toPath()).contains("v2"));
    }

    @Test
    public void installRefusesStagingChangedAfterOwnershipWasWritten() throws Exception {
        DatapackIngestService.Entry entry = new DatapackIngestService.Entry();
        entry.id = "changed-source";
        entry.url = "https://example.test/changed.zip";
        File staging = datapackDirectory("changed-source");
        DatapackOwnership.writeOwnership(staging, entry);
        Files.writeString(new File(staging, "late-change.txt").toPath(), "changed", StandardCharsets.UTF_8);
        KList<File> worlds = new KList<>();
        worlds.add(temporaryFolder.newFolder("changed-source-world"));

        try {
            DatapackInstall.install(staging, worlds, entry, false);
            fail("Expected modified staging to be rejected");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("committed manifest entry"));
        }
        assertFalse(new File(worlds.get(0), entry.id).exists());
    }

    @Test
    public void overrideStrippingFailsWhenAForbiddenTreeRemains() throws Exception {
        File datapack = datapackDirectory("failed-override-strip");
        File override = new File(datapack, "data/minecraft/worldgen/structure/test.json");
        assertTrue(override.getParentFile().mkdirs());
        Files.writeString(override.toPath(), "{}", StandardCharsets.UTF_8);

        try {
            DatapackPackMetadata.stripVanillaStructureOverrides(datapack, ignored -> {
            });
            fail("Expected a retained vanilla structure override to block installation");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("vanilla structure override tree"));
        }

        assertTrue(override.isFile());
        assertFalse(new File(datapack, ".iris-overrides-stripped").exists());
    }

    @Test
    public void multiWorldInstallPreflightsEveryTargetBeforePublishing() throws Exception {
        DatapackIngestService.Entry entry = new DatapackIngestService.Entry();
        entry.id = "managed";
        entry.url = "https://example.test/managed.zip";

        File staging = datapackDirectory("source");
        Files.writeString(new File(staging, "value.txt").toPath(), "new", StandardCharsets.UTF_8);
        DatapackOwnership.writeOwnership(staging, entry);

        File firstWorld = temporaryFolder.newFolder("first-world-datapacks");
        File firstTarget = new File(firstWorld, entry.id);
        assertTrue(firstTarget.mkdirs());
        Files.copy(new File(staging, "pack.mcmeta").toPath(), new File(firstTarget, "pack.mcmeta").toPath());
        Files.writeString(new File(firstTarget, "value.txt").toPath(), "old", StandardCharsets.UTF_8);
        DatapackOwnership.writeOwnership(firstTarget, entry);

        File secondWorld = temporaryFolder.newFolder("second-world-datapacks");
        File unmanagedTarget = new File(secondWorld, entry.id);
        assertTrue(unmanagedTarget.mkdirs());
        Files.copy(new File(staging, "pack.mcmeta").toPath(), new File(unmanagedTarget, "pack.mcmeta").toPath());
        Files.writeString(new File(unmanagedTarget, "value.txt").toPath(), "unmanaged", StandardCharsets.UTF_8);

        KList<File> worlds = new KList<>();
        worlds.add(firstWorld);
        worlds.add(secondWorld);
        try {
            DatapackInstall.install(staging, worlds, entry, false);
            fail("Expected unmanaged second target to abort the transaction");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("unmanaged datapack"));
        }

        assertEquals("old", Files.readString(new File(firstTarget, "value.txt").toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void exactManagedTargetSkipsPreparedCopyAndScratch() throws Exception {
        DatapackIngestService.Entry entry = entry("exact-managed", "v1", "1", "sha");
        File staging = datapackDirectory("exact-managed-source");
        Files.writeString(new File(staging, "value.txt").toPath(), "same", StandardCharsets.UTF_8);
        DatapackOwnership.writeOwnership(staging, entry);

        File targetRoot = temporaryFolder.newFolder("exact-managed-target-root");
        File worldFolder = new File(targetRoot, "datapacks");
        assertTrue(worldFolder.mkdir());
        File target = new File(worldFolder, entry.id);
        writeManagedDatapack(target, entry, "same");
        File scratch = new File(targetRoot, ".iris-datapack-install");

        InstallPlan plan = DatapackInstallPlanner.prepareInstall(
                staging,
                worldFolder,
                entry,
                ownershipHash(staging),
                false,
                null
        );

        assertFalse(plan.publishRequired());
        assertFalse(plan.contentChanged());
        assertFalse(scratch.exists());
        assertTrue(plan.pending() == null || !plan.pending().exists());
    }

    @Test
    public void stagedMutationBeforePrecommitRejectsAndRollsBackPublishedWorlds() throws Exception {
        PreparedMixedInstall fixture = preparedMixedInstall("staged-precommit-mutation");
        Path stagedValue = new File(fixture.staging(), "value.txt").toPath();
        FileTime originalTime = Files.getLastModifiedTime(stagedValue);
        Files.writeString(stagedValue, "bad", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(stagedValue, originalTime);

        try {
            DatapackInstall.verifyInstallExecution(fixture.execution());
            fail("Expected changed staging to block the prepared install");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("staging changed"));
            DatapackInstall.rollbackInstallExecutions(List.of(fixture.execution()), expected);
            assertEquals(0, expected.getSuppressed().length);
        }

        assertEquals("new", Files.readString(
                new File(fixture.unchangedTarget(), "value.txt").toPath(), StandardCharsets.UTF_8));
        assertEquals("old", Files.readString(
                new File(fixture.changedTarget(), "value.txt").toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void unchangedTargetMutationBeforePrecommitRejectsAndRollsBackPublishedWorlds() throws Exception {
        PreparedMixedInstall fixture = preparedMixedInstall("unchanged-precommit-mutation");
        Path unchangedValue = new File(fixture.unchangedTarget(), "value.txt").toPath();
        FileTime originalTime = Files.getLastModifiedTime(unchangedValue);
        Files.writeString(unchangedValue, "bad", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(unchangedValue, originalTime);

        try {
            DatapackInstall.verifyInstallExecution(fixture.execution());
            fail("Expected changed unchanged-target snapshot to block the prepared install");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("unchanged datapack target"));
            DatapackInstall.rollbackInstallExecutions(List.of(fixture.execution()), expected);
            assertEquals(0, expected.getSuppressed().length);
        }

        assertEquals("bad", Files.readString(unchangedValue, StandardCharsets.UTF_8));
        assertEquals("old", Files.readString(
                new File(fixture.changedTarget(), "value.txt").toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void verifiedFreshInstallCommitsAfterExtractedDirectoryIsDeleted() throws Exception {
        PreparedVerifiedFreshInstall fixture = preparedVerifiedFreshInstall(
                "verified-fresh-deleted-extraction");
        DatapackInstall.deleteInstallScratch(
                fixture.extractedDir(), "verified fresh datapack extraction");
        assertFalse(fixture.extractedDir().exists());

        DatapackInstall.verifyInstallExecution(fixture.execution());
        writeManifest(fixture.root(), fixture.entry());
        DatapackInstall.finishInstallExecution(fixture.execution());

        for (File target : List.of(fixture.worldTarget(), fixture.canonicalTarget())) {
            assertEquals("new", Files.readString(
                    new File(target, "value.txt").toPath(), StandardCharsets.UTF_8));
            assertTrue(DatapackStagingGuard.isUsableStaging(target, fixture.entry()));
        }
    }

    @Test
    public void publishedTargetMutationRollsBackUnaffectedParticipantsAndRemainsFailClosed() throws Exception {
        PreparedVerifiedFreshInstall fixture = preparedVerifiedFreshInstall(
                "published-precommit-mutation");
        Path publishedValue = new File(fixture.worldTarget(), "value.txt").toPath();
        FileTime originalTime = Files.getLastModifiedTime(publishedValue);
        Files.writeString(publishedValue, "bad", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(publishedValue, originalTime);

        try {
            DatapackInstall.verifyInstallExecution(fixture.execution());
            fail("Expected changed published target to block the prepared install");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("published datapack target"));
            DatapackInstall.rollbackInstallExecutions(List.of(fixture.execution()), expected);
            assertEquals(1, expected.getSuppressed().length);
        }

        assertEquals("bad", Files.readString(publishedValue, StandardCharsets.UTF_8));
        assertFalse(fixture.canonicalTarget().exists());
        File transactionRoot = new File(fixture.root(), ".iris-datapack-transactions");
        File[] transactions = transactionRoot.listFiles(File::isDirectory);
        assertTrue(transactions != null && transactions.length == 1);
    }

    @Test
    public void changedManagedTargetStillPreparesPublication() throws Exception {
        DatapackIngestService.Entry entry = entry("changed-managed", "v1", "1", "sha");
        File staging = datapackDirectory("changed-managed-source");
        Files.writeString(new File(staging, "value.txt").toPath(), "new", StandardCharsets.UTF_8);
        DatapackOwnership.writeOwnership(staging, entry);

        File targetRoot = temporaryFolder.newFolder("changed-managed-target-root");
        File worldFolder = new File(targetRoot, "datapacks");
        assertTrue(worldFolder.mkdir());
        writeManagedDatapack(new File(worldFolder, entry.id), entry, "old");

        InstallPlan plan = DatapackInstallPlanner.prepareInstall(
                staging,
                worldFolder,
                entry,
                ownershipHash(staging),
                false,
                null
        );

        assertTrue(plan.publishRequired());
        assertTrue(plan.contentChanged());
        assertTrue(plan.pending().isDirectory());
        assertTrue(plan.pendingRoot().isDirectory());
    }

    @Test
    public void changedOwnershipStillPreparesPublicationForExactContent() throws Exception {
        DatapackIngestService.Entry entry = entry("changed-ownership", "v2", "2", "new-sha");
        File staging = datapackDirectory("changed-ownership-source");
        Files.writeString(new File(staging, "value.txt").toPath(), "same", StandardCharsets.UTF_8);
        DatapackOwnership.writeOwnership(staging, entry);

        File targetRoot = temporaryFolder.newFolder("changed-ownership-target-root");
        File worldFolder = new File(targetRoot, "datapacks");
        assertTrue(worldFolder.mkdir());
        DatapackIngestService.Entry prior = entry("changed-ownership", "v1", "1", "old-sha");
        File target = new File(worldFolder, entry.id);
        writeManagedDatapack(target, prior, "same");

        InstallPlan plan = DatapackInstallPlanner.prepareInstall(
                staging,
                worldFolder,
                entry,
                ownershipHash(staging),
                false,
                null
        );

        assertTrue(plan.publishRequired());
        assertFalse(plan.contentChanged());
        assertTrue(plan.pending().isDirectory());
    }

    @Test
    public void overrideStrippingStillPreparesPublicationForExactContent() throws Exception {
        DatapackIngestService.Entry entry = entry("strip-managed", "v1", "1", "sha");
        File staging = datapackDirectory("strip-managed-source");
        Files.writeString(new File(staging, "value.txt").toPath(), "same", StandardCharsets.UTF_8);
        DatapackOwnership.writeOwnership(staging, entry);

        File targetRoot = temporaryFolder.newFolder("strip-managed-target-root");
        File worldFolder = new File(targetRoot, "datapacks");
        assertTrue(worldFolder.mkdir());
        writeManagedDatapack(new File(worldFolder, entry.id), entry, "same");

        InstallPlan plan = DatapackInstallPlanner.prepareInstall(
                staging,
                worldFolder,
                entry,
                ownershipHash(staging),
                true,
                null
        );

        assertTrue(plan.publishRequired());
        assertTrue(plan.contentChanged());
        assertTrue(new File(plan.pending(), ".iris-overrides-stripped").isFile());
    }

    @Test
    public void exactLegacyUnmarkedWorldInstallCanReceiveManagedOwnership() throws Exception {
        DatapackIngestService.Entry entry = entry("managed", "v2", "2", "sha");
        File staging = datapackDirectory("legacy-ownership-source");
        Files.writeString(new File(staging, "value.txt").toPath(), "same", StandardCharsets.UTF_8);
        DatapackOwnership.writeOwnership(staging, entry);
        File world = temporaryFolder.newFolder("legacy-ownership-world");
        File target = new File(world, entry.id);
        assertTrue(target.mkdirs());
        Files.copy(new File(staging, "pack.mcmeta").toPath(), new File(target, "pack.mcmeta").toPath());
        Files.copy(new File(staging, "value.txt").toPath(), new File(target, "value.txt").toPath());
        KList<File> worlds = new KList<>();
        worlds.add(world);

        InstallResult result =
                DatapackInstall.install(staging, worlds, entry, false);

        assertFalse(result.changed());
        assertTrue(new File(target, ".iris-managed.json").isFile());
        assertTrue(DatapackStagingGuard.isUsableStaging(target, entry));
    }

    @Test
    public void modifiedLegacyUnmarkedWorldInstallCannotBeAdopted() throws Exception {
        DatapackIngestService.Entry entry = entry("managed", "v2", "2", "sha");
        File staging = datapackDirectory("modified-legacy-source");
        Files.writeString(new File(staging, "value.txt").toPath(), "desired", StandardCharsets.UTF_8);
        DatapackOwnership.writeOwnership(staging, entry);
        File world = temporaryFolder.newFolder("modified-legacy-world");
        File target = new File(world, entry.id);
        assertTrue(target.mkdirs());
        Files.copy(new File(staging, "pack.mcmeta").toPath(), new File(target, "pack.mcmeta").toPath());
        Files.writeString(new File(target, "value.txt").toPath(), "modified", StandardCharsets.UTF_8);
        KList<File> worlds = new KList<>();
        worlds.add(world);

        try {
            DatapackInstall.install(staging, worlds, entry, false);
            fail("Expected modified unmanaged datapack adoption to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("unmanaged datapack"));
        }

        assertEquals("modified", Files.readString(
                new File(target, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertFalse(new File(target, ".iris-managed.json").exists());
    }

    @Test
    public void verifiedFreshInstallPublishesCanonicalManagedStaging() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("verified-first-install", false, true, false);

        InstallPlan plan = prepareLegacyStagingPlan(fixture);
        DatapackInstallPlanner.publishInstallPlan(plan);

        assertEquals("new", Files.readString(
                new File(fixture.target(), "value.txt").toPath(), StandardCharsets.UTF_8));
        assertTrue(new File(fixture.target(), ".iris-managed.json").isFile());
    }

    @Test
    public void verifiedLegacyStagingUpgradeReplacesDifferingUnmarkedTree() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("verified-legacy-upgrade", true, true, true);

        InstallPlan plan = prepareLegacyStagingPlan(fixture);
        DatapackInstallPlanner.publishInstallPlan(plan);

        assertEquals("new", Files.readString(
                new File(fixture.target(), "value.txt").toPath(), StandardCharsets.UTF_8));
        assertFalse(new File(fixture.target(), ".DS_Store").exists());
        assertTrue(DatapackStagingGuard.isUsableStaging(fixture.target(), fixture.desired()));
        assertEquals("old", Files.readString(
                new File(plan.backup(), "value.txt").toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void caseVariantCanonicalStagingPathIsAcceptedOnCaseInsensitiveFilesystems() throws Exception {
        File parent = temporaryFolder.newFolder("case-variant-parent").toPath().toRealPath().toFile();
        File root = new File(parent, "iris-storage");
        assertTrue(root.mkdir());
        File stagingRoot = new File(root, "staging");
        assertTrue(stagingRoot.mkdir());
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        writeManifest(root, entry);
        File source = new File(stagingRoot, ".pending-managed-" + UUID.randomUUID());
        writeManagedDatapack(source, entry, "new");
        File caseVariantRoot = new File(parent, "IRIS-STORAGE");
        File caseVariantStagingRoot = new File(caseVariantRoot, "staging");
        File caseVariantSource = new File(caseVariantStagingRoot, source.getName());
        Assume.assumeTrue(Files.exists(caseVariantRoot.toPath()));
        Assume.assumeTrue(Files.isSameFile(root.toPath(), caseVariantRoot.toPath()));

        VerifiedStagingInstall authorization =
                DatapackStagingGuard.authorizeVerifiedStagingInstall(
                        caseVariantRoot, caseVariantStagingRoot, caseVariantSource, entry);

        assertTrue(authorization != null);
    }

    @Test
    public void symbolicLinkAncestorCannotAuthorizeVerifiedStaging() throws Exception {
        File parent = temporaryFolder.newFolder("symbolic-authority-parent").toPath().toRealPath().toFile();
        File container = new File(parent, "container");
        assertTrue(container.mkdir());
        File root = new File(container, "iris-storage");
        assertTrue(root.mkdir());
        File stagingRoot = new File(root, "staging");
        assertTrue(stagingRoot.mkdir());
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        writeManifest(root, entry);
        File source = new File(stagingRoot, ".pending-managed-" + UUID.randomUUID());
        writeManagedDatapack(source, entry, "new");
        Path alias = new File(parent, "container-link").toPath();
        try {
            Files.createSymbolicLink(alias, container.toPath());
        } catch (IOException | UnsupportedOperationException unavailable) {
            Assume.assumeNoException(unavailable);
        }
        File aliasedRoot = alias.resolve("iris-storage").toFile();
        File aliasedStagingRoot = new File(aliasedRoot, "staging");
        File aliasedSource = new File(aliasedStagingRoot, source.getName());

        try {
            DatapackStagingGuard.authorizeVerifiedStagingInstall(
                    aliasedRoot, aliasedStagingRoot, aliasedSource, entry);
            fail("Expected symbolic-link staging authority to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("symbolic-link component"));
        }
    }

    @Test
    public void sameVersionLegacyStagingMetadataForcesManagedRestageAndRestart() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture(
                "same-version-legacy-restage", true, true, true, true);

        InstallPlan plan = prepareLegacyStagingPlan(fixture);
        DatapackInstallPlanner.publishInstallPlan(plan);

        assertFalse(plan.contentChanged());
        assertTrue(DatapackIngestService.freshInstallRequiresRestart(plan.contentChanged(), true));
        assertEquals("same", Files.readString(
                new File(fixture.target(), "value.txt").toPath(), StandardCharsets.UTF_8));
        assertFalse(new File(fixture.target(), ".DS_Store").exists());
        assertTrue(DatapackStagingGuard.isUsableStaging(fixture.target(), fixture.desired()));
    }

    @Test
    public void uncommittedManifestCannotAuthorizeDifferingLegacyStaging() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("uncommitted-legacy-upgrade", false, true, true);

        assertLegacyStagingPreparationRejected(fixture, "unmanaged datapack");
        assertEquals("old", Files.readString(
                new File(fixture.target(), "value.txt").toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void mismatchedManifestUrlCannotAuthorizeDifferingLegacyStaging() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("mismatched-legacy-upgrade", true, false, true);

        assertLegacyStagingPreparationRejected(fixture, "authority conflicts");
        assertEquals("old", Files.readString(
                new File(fixture.target(), "value.txt").toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void malformedOwnershipMarkerCannotMasqueradeAsLegacyStaging() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("malformed-legacy-marker", true, true, true);
        Files.writeString(new File(fixture.target(), ".iris-managed.json").toPath(), "{broken",
                StandardCharsets.UTF_8);

        assertLegacyStagingPreparationRejected(fixture, "Invalid Iris datapack ownership marker");
    }

    @Test
    public void ownershipMarkerDirectoryCannotMasqueradeAsLegacyStaging() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("directory-legacy-marker", true, true, true);
        assertTrue(new File(fixture.target(), ".iris-managed.json").mkdir());

        assertLegacyStagingPreparationRejected(fixture, "Invalid Iris datapack ownership marker");
    }

    @Test
    public void ownershipMarkerSymlinkCannotMasqueradeAsLegacyStaging() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("symlink-legacy-marker", true, true, true);
        Path markerTarget = new File(fixture.root(), "marker-target.json").toPath();
        Files.writeString(markerTarget, "{}", StandardCharsets.UTF_8);
        try {
            Files.createSymbolicLink(new File(fixture.target(), ".iris-managed.json").toPath(), markerTarget);
        } catch (IOException | UnsupportedOperationException unavailable) {
            Assume.assumeNoException(unavailable);
        }

        assertLegacyStagingPreparationRejected(fixture, "unsupported file");
    }

    @Test
    public void validWrongOwnerMarkerCannotMasqueradeAsLegacyStaging() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("wrong-owner-legacy-marker", true, true, true);
        DatapackIngestService.Entry other = entry("other", "v1", "1", "other-sha");
        DatapackOwnership.writeOwnership(fixture.target(), other);

        assertLegacyStagingPreparationRejected(fixture, "ownership mismatch");
    }

    @Test
    public void verifiedStagingAuthorityNeverAppliesToAModifiedWorldTarget() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("world-authority-boundary", true, true, true);
        File world = new File(fixture.root(), "world-datapacks");
        assertTrue(world.mkdir());
        File worldTarget = new File(world, fixture.desired().id);
        writeLegacyDatapack(worldTarget, "world");

        try {
            DatapackInstallPlanner.prepareInstall(
                    fixture.source(), world, fixture.desired(), fixture.sourceHash(), false,
                    fixture.authorization());
            fail("Expected verified staging authority to reject a world target");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("unmanaged datapack"));
        }
        assertEquals("world", Files.readString(
                new File(worldTarget, "value.txt").toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void exactLegacyStagingCopyInAWorldCanMigrateBeforeCanonicalRestage() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("exact-world-legacy-copy", true, true, true);
        File world = temporaryFolder.newFolder("exact-world-legacy-copy-target");
        File worldTarget = new File(world, fixture.desired().id);
        IO.copyDirectory(fixture.target().toPath(), worldTarget.toPath());

        InstallPlan worldPlan = DatapackInstallPlanner.prepareInstall(
                fixture.source(), world, fixture.desired(), fixture.sourceHash(), false,
                fixture.authorization());
        DatapackInstallPlanner.publishInstallPlan(worldPlan);
        InstallPlan stagingPlan = prepareLegacyStagingPlan(fixture);
        DatapackInstallPlanner.publishInstallPlan(stagingPlan);

        assertEquals("new", Files.readString(
                new File(worldTarget, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertTrue(new File(worldTarget, ".iris-managed.json").isFile());
        assertEquals("new", Files.readString(
                new File(fixture.target(), "value.txt").toPath(), StandardCharsets.UTF_8));
        assertTrue(new File(fixture.target(), ".iris-managed.json").isFile());
    }

    @Test
    public void productionExecutionMigratesTwoExactLegacyWorldCopiesBeforeCanonicalStaging() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("multi-world-legacy-copy", true, true, true);
        File firstWorld = temporaryFolder.newFolder("multi-world-legacy-copy-first");
        File secondWorld = temporaryFolder.newFolder("multi-world-legacy-copy-second");
        File firstTarget = new File(firstWorld, fixture.desired().id);
        File secondTarget = new File(secondWorld, fixture.desired().id);
        IO.copyDirectory(fixture.target().toPath(), firstTarget.toPath());
        IO.copyDirectory(fixture.target().toPath(), secondTarget.toPath());
        KList<File> worlds = new KList<>();
        worlds.add(firstWorld);
        worlds.add(secondWorld);

        InstallExecution execution =
                DatapackInstall.prepareInstallExecution(
                        fixture.source(),
                        worlds,
                        fixture.desired(),
                        false,
                        fixture.root(),
                        fixture.authorization());
        DatapackInstall.verifyInstallExecution(execution);
        writeManifest(fixture.root(), fixture.desired());
        DatapackInstall.finishInstallExecution(execution);

        for (File target : List.of(firstTarget, secondTarget, fixture.target())) {
            assertEquals("new", Files.readString(
                    new File(target, "value.txt").toPath(), StandardCharsets.UTF_8));
            assertTrue(new File(target, ".iris-managed.json").isFile());
            assertFalse(new File(target, ".DS_Store").exists());
        }
    }

    @Test
    public void modifiedSecondLegacyWorldPreventsEveryParticipantFromPublishing() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("mixed-world-legacy-copy", true, true, true);
        File firstWorld = temporaryFolder.newFolder("mixed-world-legacy-copy-first");
        File secondWorld = temporaryFolder.newFolder("mixed-world-legacy-copy-second");
        File firstTarget = new File(firstWorld, fixture.desired().id);
        File secondTarget = new File(secondWorld, fixture.desired().id);
        IO.copyDirectory(fixture.target().toPath(), firstTarget.toPath());
        writeLegacyDatapack(secondTarget, "modified");
        KList<File> worlds = new KList<>();
        worlds.add(firstWorld);
        worlds.add(secondWorld);

        try {
            DatapackInstall.prepareInstallExecution(
                    fixture.source(),
                    worlds,
                    fixture.desired(),
                    false,
                    fixture.root(),
                    fixture.authorization());
            fail("Expected a modified legacy world to block the complete install");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("unmanaged datapack"));
        }

        assertEquals("old", Files.readString(
                new File(firstTarget, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertEquals("modified", Files.readString(
                new File(secondTarget, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertEquals("old", Files.readString(
                new File(fixture.target(), "value.txt").toPath(), StandardCharsets.UTF_8));
        assertFalse(new File(firstTarget, ".iris-managed.json").exists());
        assertFalse(new File(secondTarget, ".iris-managed.json").exists());
        assertFalse(new File(fixture.target(), ".iris-managed.json").exists());
    }

    @Test
    public void coordinatorRollbackRestoresLegacyWorldCopiesAndCanonicalStaging() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("rollback-world-legacy-copy", true, true, true);
        File firstWorld = temporaryFolder.newFolder("rollback-world-legacy-copy-first");
        File secondWorld = temporaryFolder.newFolder("rollback-world-legacy-copy-second");
        File firstTarget = new File(firstWorld, fixture.desired().id);
        File secondTarget = new File(secondWorld, fixture.desired().id);
        IO.copyDirectory(fixture.target().toPath(), firstTarget.toPath());
        IO.copyDirectory(fixture.target().toPath(), secondTarget.toPath());
        KList<File> worlds = new KList<>();
        worlds.add(firstWorld);
        worlds.add(secondWorld);
        InstallExecution execution =
                DatapackInstall.prepareInstallExecution(
                        fixture.source(),
                        worlds,
                        fixture.desired(),
                        false,
                        fixture.root(),
                        fixture.authorization());

        IOException failure = new IOException("manifest publication failed");
        DatapackInstall.rollbackInstallExecutions(List.of(execution), failure);

        assertEquals(0, failure.getSuppressed().length);
        for (File target : List.of(firstTarget, secondTarget, fixture.target())) {
            assertEquals("old", Files.readString(
                    new File(target, "value.txt").toPath(), StandardCharsets.UTF_8));
            assertTrue(new File(target, ".DS_Store").isFile());
            assertFalse(new File(target, ".iris-managed.json").exists());
        }
    }

    @Test
    public void changedCanonicalLegacySnapshotBlocksPreparedWorldMigration() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("changed-canonical-world-proof", true, true, true);
        File world = temporaryFolder.newFolder("changed-canonical-world-proof-target");
        File worldTarget = new File(world, fixture.desired().id);
        IO.copyDirectory(fixture.target().toPath(), worldTarget.toPath());
        InstallPlan worldPlan = DatapackInstallPlanner.prepareInstall(
                fixture.source(), world, fixture.desired(), fixture.sourceHash(), false,
                fixture.authorization());
        Files.writeString(new File(fixture.target(), "value.txt").toPath(), "changed", StandardCharsets.UTF_8);

        try {
            DatapackInstallPlanner.publishInstallPlan(worldPlan);
            fail("Expected changed canonical legacy staging to block world migration");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("canonical legacy datapack staging target"));
        }
        assertEquals("old", Files.readString(
                new File(worldTarget, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertFalse(new File(worldTarget, ".iris-managed.json").exists());
    }

    @Test
    public void swappedCanonicalLegacySnapshotBlocksPreparedWorldMigration() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("swapped-canonical-world-proof", true, true, true);
        File world = temporaryFolder.newFolder("swapped-canonical-world-proof-target");
        File worldTarget = new File(world, fixture.desired().id);
        IO.copyDirectory(fixture.target().toPath(), worldTarget.toPath());
        InstallPlan worldPlan = DatapackInstallPlanner.prepareInstall(
                fixture.source(), world, fixture.desired(), fixture.sourceHash(), false,
                fixture.authorization());
        File displaced = new File(fixture.stagingRoot(), "displaced-managed");
        Files.move(fixture.target().toPath(), displaced.toPath());
        IO.copyDirectory(displaced.toPath(), fixture.target().toPath());

        try {
            DatapackInstallPlanner.publishInstallPlan(worldPlan);
            fail("Expected swapped canonical legacy staging to block world migration");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("identity changed"));
        }
        assertEquals("old", Files.readString(
                new File(worldTarget, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertFalse(new File(worldTarget, ".iris-managed.json").exists());
    }

    @Test
    public void changedManifestCannotReuseLegacyStagingAuthority() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("changed-manifest-authority", true, true, true);
        writeManifest(fixture.root(), null);

        assertLegacyStagingPreparationRejected(fixture, "authority changed");
    }

    @Test
    public void sameUrlManifestMetadataChangeBlocksPreparedWorldMigration() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("changed-world-manifest-metadata", true, true, true);
        File world = temporaryFolder.newFolder("changed-world-manifest-metadata-target");
        File worldTarget = new File(world, fixture.desired().id);
        IO.copyDirectory(fixture.target().toPath(), worldTarget.toPath());
        InstallPlan worldPlan = DatapackInstallPlanner.prepareInstall(
                fixture.source(), world, fixture.desired(), fixture.sourceHash(), false,
                fixture.authorization());
        DatapackIngestService.Entry changed = entry("managed", "other-version", "other", "other-sha");
        writeManifest(fixture.root(), changed);

        try {
            DatapackInstallPlanner.publishInstallPlan(worldPlan);
            fail("Expected changed manifest metadata to block world migration");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("authority changed"));
        }
        assertEquals("old", Files.readString(
                new File(worldTarget, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertFalse(new File(worldTarget, ".iris-managed.json").exists());
    }

    @Test
    public void swappedStagingRootCannotReuseVerifiedAuthority() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("swapped-staging-authority", true, true, true);
        Path displaced = new File(fixture.root(), "displaced-staging").toPath();
        Files.move(fixture.stagingRoot().toPath(), displaced);
        assertTrue(fixture.stagingRoot().mkdir());

        assertLegacyStagingPreparationRejected(fixture, "Changed or unsafe datapack staging root");
    }

    @Test
    public void duplicateWorldParticipantsAreRejectedBeforePublication() throws Exception {
        DatapackIngestService.Entry entry = entry("duplicate-world", "v1", "1", "sha");
        File source = datapackDirectory("duplicate-world-source");
        DatapackOwnership.writeOwnership(source, entry);
        File world = temporaryFolder.newFolder("duplicate-world-target");
        KList<File> worlds = new KList<>();
        worlds.add(world);
        worlds.add(world);

        try {
            DatapackInstall.install(source, worlds, entry, false);
            fail("Expected duplicate world install participants to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Duplicate datapack install target"));
        }
        assertFalse(new File(world, entry.id).exists());
    }

    @Test
    public void symbolicLinkWorldParticipantIsRejectedBeforePublication() throws Exception {
        DatapackIngestService.Entry entry = entry("aliased-world", "v1", "1", "sha");
        File source = datapackDirectory("aliased-world-source");
        DatapackOwnership.writeOwnership(source, entry);
        File world = temporaryFolder.newFolder("aliased-world-target");
        Path alias = new File(temporaryFolder.getRoot(), "aliased-world-link").toPath();
        try {
            Files.createSymbolicLink(alias, world.toPath());
        } catch (IOException | UnsupportedOperationException unavailable) {
            Assume.assumeNoException(unavailable);
        }
        KList<File> worlds = new KList<>();
        worlds.add(world);
        worlds.add(alias.toFile());

        try {
            DatapackInstall.install(source, worlds, entry, false);
            fail("Expected symbolic-link world participant to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Invalid datapack install root"));
        }
        assertFalse(new File(world, entry.id).exists());
    }

    @Test
    public void worldParticipantAliasThroughASymbolicParentIsRejected() throws Exception {
        DatapackIngestService.Entry entry = entry("parent-aliased-world", "v1", "1", "sha");
        File source = datapackDirectory("parent-aliased-world-source");
        DatapackOwnership.writeOwnership(source, entry);
        File realParent = temporaryFolder.newFolder("parent-aliased-world-root");
        File realWorld = new File(realParent, "datapacks");
        assertTrue(realWorld.mkdir());
        Path aliasParent = new File(temporaryFolder.getRoot(), "parent-aliased-world-link").toPath();
        try {
            Files.createSymbolicLink(aliasParent, realParent.toPath());
        } catch (IOException | UnsupportedOperationException unavailable) {
            Assume.assumeNoException(unavailable);
        }
        File aliasedWorld = aliasParent.resolve("datapacks").toFile();
        KList<File> worlds = new KList<>();
        worlds.add(realWorld);
        worlds.add(aliasedWorld);

        try {
            DatapackInstall.install(source, worlds, entry, false);
            fail("Expected real-path world participant alias to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Duplicate datapack install target"));
        }
        assertFalse(new File(realWorld, entry.id).exists());
    }

    @Test
    public void changedLegacyTargetIsRejectedImmediatelyBeforePublication() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("changed-target-publication", true, true, true);
        InstallPlan plan = prepareLegacyStagingPlan(fixture);
        Files.writeString(new File(plan.target(), "value.txt").toPath(), "changed", StandardCharsets.UTF_8);

        assertInstallPublicationRejected(plan, "content changed");
        assertEquals("changed", Files.readString(
                new File(plan.target(), "value.txt").toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void changedLegacyMarkerIsRejectedImmediatelyBeforePublication() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("changed-marker-publication", true, true, true);
        InstallPlan plan = prepareLegacyStagingPlan(fixture);
        Files.writeString(new File(plan.target(), ".iris-managed.json").toPath(), "{}", StandardCharsets.UTF_8);

        assertInstallPublicationRejected(plan, "content changed");
        assertTrue(new File(plan.target(), ".iris-managed.json").isFile());
    }

    @Test
    public void byteIdenticalLegacyTargetSwapIsRejectedBeforePublication() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("swapped-target-publication", true, true, true);
        InstallPlan plan = prepareLegacyStagingPlan(fixture);
        File displaced = new File(fixture.stagingRoot(), "displaced-target");
        Files.move(plan.target().toPath(), displaced.toPath());
        writeLegacyDatapack(plan.target(), "old");

        assertInstallPublicationRejected(plan, "identity changed");
        assertTrue(displaced.isDirectory());
    }

    @Test
    public void changedPreparedContentIsRejectedBeforePublication() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("changed-pending-publication", true, true, true);
        InstallPlan plan = prepareLegacyStagingPlan(fixture);
        Files.writeString(new File(plan.pending(), "value.txt").toPath(), "changed", StandardCharsets.UTF_8);

        assertInstallPublicationRejected(plan, "content changed");
        assertEquals("old", Files.readString(
                new File(plan.target(), "value.txt").toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void changedPreparedMarkerIsRejectedBeforePublication() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("changed-pending-marker", true, true, true);
        InstallPlan plan = prepareLegacyStagingPlan(fixture);
        Files.writeString(new File(plan.pending(), ".iris-managed.json").toPath(), "{}", StandardCharsets.UTF_8);

        assertInstallPublicationRejected(plan, "content changed");
        assertEquals("old", Files.readString(
                new File(plan.target(), "value.txt").toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void swappedTargetRootIsRejectedBeforePublication() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("swapped-target-root", true, true, true);
        InstallPlan plan = prepareLegacyStagingPlan(fixture);
        Path displaced = new File(fixture.root(), "displaced-target-root").toPath();
        Files.move(fixture.stagingRoot().toPath(), displaced);
        assertTrue(fixture.stagingRoot().mkdir());

        assertInstallPublicationRejected(plan, "Changed or unsafe datapack target root");
        assertEquals("old", Files.readString(
                displaced.resolve("managed/value.txt"), StandardCharsets.UTF_8));
    }

    @Test
    public void swappedInstallScratchRootIsRejectedBeforePublication() throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture("swapped-install-scratch", true, true, true);
        InstallPlan plan = prepareLegacyStagingPlan(fixture);
        Path displaced = new File(fixture.root(), "displaced-install-scratch").toPath();
        Files.move(plan.pendingRoot().toPath(), displaced);
        assertTrue(plan.pendingRoot().mkdir());

        assertInstallPublicationRejected(plan, "Changed or unsafe datapack install scratch root");
        assertEquals("old", Files.readString(
                new File(plan.target(), "value.txt").toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void recoveredCommittedStagingRequiresRestartWhenContentIsUnchanged() {
        assertTrue(DatapackIngestService.freshInstallRequiresRestart(false, true));
        assertFalse(DatapackIngestService.freshInstallRequiresRestart(false, false));
        assertTrue(DatapackIngestService.freshInstallRequiresRestart(true, false));
    }

    @Test
    public void installedStructureScopeReadsEffectiveStructureSetsWithoutManifestMigration() throws Exception {
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        File installed = datapackDirectory("effective-scope");
        Path set = new File(installed,
                "data/nova_structures/worldgen/structure_set/illager_barracks.json").toPath();
        Files.createDirectories(set.getParent());
        Files.writeString(set, "{}", StandardCharsets.UTF_8);
        DatapackOwnership.writeOwnership(installed, entry);

        DatapackIngestService.StructureScopeResources resources =
                DatapackIngestService.scanInstalledStructureScope(installed, entry);

        assertEquals(entry.url, resources.source());
        assertEquals(List.of(), resources.structureKeys());
        assertEquals(List.of("nova_structures:illager_barracks"), resources.structureSetKeys());
    }

    @Test
    public void installedStructureScopeDoesNotClaimOverridesStrippedFromEffectiveCopy() throws Exception {
        DatapackIngestService.Entry entry = entry("managed-stripped", "v1", "1", "sha");
        File installed = datapackDirectory("effective-stripped-scope");
        Path retained = new File(installed,
                "data/nova_structures/worldgen/structure_set/taverns.json").toPath();
        Files.createDirectories(retained.getParent());
        Files.writeString(retained, "{}", StandardCharsets.UTF_8);
        Files.writeString(new File(installed, ".iris-overrides-stripped").toPath(), "", StandardCharsets.UTF_8);
        DatapackOwnership.writeOwnership(installed, entry);

        DatapackIngestService.StructureScopeResources resources =
                DatapackIngestService.scanInstalledStructureScope(installed, entry);

        assertEquals(List.of("nova_structures:taverns"), resources.structureSetKeys());
        assertFalse(resources.structureSetKeys().contains("minecraft:villages"));
    }

    @Test
    public void unchangedContentRefreshesRecoverableOwnershipMetadataWithoutRestart() throws Exception {
        DatapackIngestService.Entry current = new DatapackIngestService.Entry();
        current.id = "managed";
        current.url = "https://example.test/current.zip";
        current.versionId = "v2";
        current.versionNumber = "2";
        current.sha1 = "current-hash";
        current.structureKeys = List.of("example:castle");

        File staging = datapackDirectory("metadata-source");
        Files.writeString(new File(staging, "value.txt").toPath(), "same", StandardCharsets.UTF_8);
        DatapackOwnership.writeOwnership(staging, current);

        File world = temporaryFolder.newFolder("metadata-world-datapacks");
        File target = new File(world, current.id);
        assertTrue(target.mkdirs());
        Files.copy(new File(staging, "pack.mcmeta").toPath(), new File(target, "pack.mcmeta").toPath());
        Files.copy(new File(staging, "value.txt").toPath(), new File(target, "value.txt").toPath());
        DatapackIngestService.Entry old = new DatapackIngestService.Entry();
        old.id = current.id;
        old.url = "https://example.test/old.zip";
        old.versionId = "v1";
        old.versionNumber = "1";
        old.sha1 = "old-hash";
        DatapackOwnership.writeOwnership(target, old);

        KList<File> worlds = new KList<>();
        worlds.add(world);
        InstallResult result = DatapackInstall.install(staging, worlds, current, false);
        String marker = Files.readString(new File(target, ".iris-managed.json").toPath(), StandardCharsets.UTF_8);

        assertFalse(result.changed());
        assertTrue(marker.contains("https://example.test/current.zip"));
        assertTrue(marker.contains("example:castle"));
        assertFalse(marker.contains("https://example.test/old.zip"));
    }

    @Test
    public void editableImportInventoryTracksOnlySourceOwnedBundleKeys() {
        DatapackIngestService.Entry entry = new DatapackIngestService.Entry();
        entry.structureKeys = List.of("nova_structures:tavern/oak");
        entry.templateKeys = List.of("nova_structures:building/house");

        Map<String, String> inventory = DatapackStructureImports.importBundleInventory(entry);

        assertEquals("nova_structures:tavern/oak", inventory.get("iris:nova_structures_tavern_oak"));
        assertEquals("nova_structures:building/house", inventory.get("iris:nova_structures/building/house"));
        assertEquals(2, inventory.size());
    }

    @Test
    public void recoveryInventoryProtectsPriorAndDesiredBundlesBeforeImport() {
        DatapackIngestService.Entry entry = entry("managed", "v2", "2", "new-sha");
        entry.structureKeys = List.of("test:castle", "test:new");
        entry.importedTargets.put("pack", "old-revision");
        entry.importedBundles.put("pack", Map.of(
                "iris:test_castle", "old:castle",
                "iris:stale", "old:stale"
        ));
        entry.structuresImported = true;

        DatapackStructureImports.prepareImportRecoveryInventory(entry, "pack");

        assertEquals(Map.of(
                "iris:test_castle", "old:castle",
                "iris:test_new", "test:new",
                "iris:stale", "old:stale"
        ), entry.importedBundles.get("pack"));
        assertFalse(entry.importedTargets.containsKey("pack"));
        assertFalse(entry.structuresImported);
    }

    @Test
    public void deterministicPartialImportIsRetainedWithoutRetryingSameRevision() {
        DatapackIngestService.Entry entry = entry("managed", "v2", "2", "sha-two");
        entry.importedBundles.put("pack", Map.of("iris:test_castle", "test:castle"));

        DatapackStructureImports.recordDeterministicImportAttempt(entry, "pack");

        assertFalse(DatapackStructureImports.importPending(entry, "pack"));
        assertEquals(Map.of("iris:test_castle", "test:castle"), entry.importedBundles.get("pack"));
        assertFalse(entry.importedTargets.containsKey("pack"));
    }

    @Test
    public void changedSourceRevisionRetriesDeterministicImportAttempt() {
        DatapackIngestService.Entry entry = entry("managed", "v2", "2", "sha-two");
        DatapackStructureImports.recordDeterministicImportAttempt(entry, "pack");

        entry.sha1 = "sha-three";

        assertTrue(DatapackStructureImports.importPending(entry, "pack"));
    }

    @Test
    public void changedImporterFormatRetriesDeterministicImportAttempt() {
        DatapackIngestService.Entry entry = entry("managed", "v2", "2", "sha-two");
        entry.importAttempts.put("pack", DatapackStructureImports.importRevision(entry, 1));

        assertTrue(DatapackStructureImports.importPending(entry, "pack"));
    }

    @Test
    public void provenanceImportFormatRetriesVersionTwoEditableImports() {
        DatapackIngestService.Entry entry = entry("managed", "v2", "2", "sha-two");
        entry.importedTargets.put("pack", DatapackStructureImports.importRevision(entry, 2));

        assertTrue(DatapackStructureImports.importPending(entry, "pack"));
    }

    @Test
    public void newTargetRetriesDeterministicImportAttempt() {
        DatapackIngestService.Entry entry = entry("managed", "v2", "2", "sha-two");
        DatapackStructureImports.recordDeterministicImportAttempt(entry, "pack-one");

        assertTrue(DatapackStructureImports.importPending(entry, "pack-two"));
    }

    @Test
    public void retryableImportPreparationClearsPriorAttempt() {
        DatapackIngestService.Entry entry = entry("managed", "v2", "2", "sha-two");
        DatapackStructureImports.recordDeterministicImportAttempt(entry, "pack");

        DatapackStructureImports.prepareImportRecoveryInventory(entry, "pack");

        assertTrue(DatapackStructureImports.importPending(entry, "pack"));
        assertFalse(entry.importAttempts.containsKey("pack"));
    }

    @Test
    public void successfulImportReplacesDeterministicAttempt() {
        DatapackIngestService.Entry entry = entry("managed", "v2", "2", "sha-two");
        DatapackStructureImports.recordDeterministicImportAttempt(entry, "pack");

        DatapackStructureImports.recordSuccessfulImport(entry, "pack");

        assertFalse(DatapackStructureImports.importPending(entry, "pack"));
        assertEquals(DatapackStructureImports.importRevision(entry), entry.importedTargets.get("pack"));
        assertFalse(entry.importAttempts.containsKey("pack"));
    }

    @Test
    public void removingOneDatapackPreservesAStillClaimedEditableBundle() throws Exception {
        File root = temporaryFolder.newFolder("shared-removal-root");
        File editablePack = temporaryFolder.newFolder("shared-removal-editable");
        StructureKey targetKey = StructureKey.parse("iris:owned");
        StructureKey sourceKey = StructureKey.parse("example:owned");
        StructureTransactionWriter writer = new StructureTransactionWriter(editablePack.toPath());
        assertEquals(StructureWriteResult.Status.ADDED, writer.writeManagedDatapack(
                importedBundle(targetKey, sourceKey, "owned"),
                StructureWriteMode.ADD_ONLY
        ).status());

        String targetId = editablePack.getAbsolutePath();
        DatapackIngestService.Entry removed = entry("removed", "v1", "1", "sha-one");
        removed.importedTargets.put(targetId, "revision-one");
        removed.importedBundles.put(targetId, Map.of("iris:owned", "example:owned"));
        DatapackIngestService.Entry retained = entry("retained", "v1", "1", "sha-two");
        retained.importedTargets.put(targetId, "revision-two");
        retained.importedBundles.put(targetId, Map.of("iris:owned", "example:owned"));
        retained.structuresImported = true;
        Files.writeString(new File(root, "manifest.json").toPath(),
                new Gson().toJson(Map.of("entries", List.of(removed, retained))),
                StandardCharsets.UTF_8);

        assertTrue(DatapackRemoval.removeLocked(null, removed.id, root, List.of()));

        assertEquals("owned", Files.readString(new File(editablePack, "objects/owned.iob").toPath()));
        assertTrue(Files.exists(writer.ownershipManifestPath(targetKey)));
        JsonObject retainedJson = JsonParser.parseString(Files.readString(
                        new File(root, "manifest.json").toPath(), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("retained", retainedJson.get("id").getAsString());
        assertFalse(retainedJson.getAsJsonObject("importedTargets").has(targetId));
        assertFalse(retainedJson.get("structuresImported").getAsBoolean());
    }

    @Test
    public void removalPreservesAKeyCollisionAndInvalidatesTheDifferentRetainedSource() throws Exception {
        File root = temporaryFolder.newFolder("collision-removal-root");
        File editablePack = temporaryFolder.newFolder("collision-removal-editable");
        StructureKey targetKey = StructureKey.parse("iris:foo_a_b");
        StructureKey currentSource = StructureKey.parse("foo:a/b");
        StructureTransactionWriter writer = new StructureTransactionWriter(editablePack.toPath());
        assertEquals(StructureWriteResult.Status.ADDED, writer.writeManagedDatapack(
                importedBundle(targetKey, currentSource, "current"),
                StructureWriteMode.ADD_ONLY
        ).status());

        String targetId = editablePack.getAbsolutePath();
        DatapackIngestService.Entry removed = entry("removed", "v1", "1", "sha-one");
        removed.importedTargets.put(targetId, "revision-one");
        removed.importedBundles.put(targetId, Map.of("iris:foo_a_b", "foo:a/b"));
        DatapackIngestService.Entry retained = entry("retained", "v1", "1", "sha-two");
        retained.importedTargets.put(targetId, "revision-two");
        retained.importedBundles.put(targetId, Map.of("iris:foo_a_b", "foo:a_b"));
        retained.structuresImported = true;
        Files.writeString(new File(root, "manifest.json").toPath(),
                new Gson().toJson(Map.of("entries", List.of(removed, retained))),
                StandardCharsets.UTF_8);

        assertTrue(DatapackRemoval.removeLocked(null, removed.id, root, List.of()));

        assertEquals("current", Files.readString(new File(editablePack, "objects/foo_a_b.iob").toPath()));
        JsonObject retainedJson = JsonParser.parseString(Files.readString(
                        new File(root, "manifest.json").toPath(), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        assertFalse(retainedJson.getAsJsonObject("importedTargets").has(targetId));
        assertFalse(retainedJson.get("structuresImported").getAsBoolean());
    }

    @Test
    public void failedUnrelatedCleanupStillInvalidatesARetainedCollisionOwner() throws Exception {
        File editablePack = temporaryFolder.newFolder("failed-collision-cleanup-editable");
        StructureKey collisionTarget = StructureKey.parse("iris:foo_a_b");
        StructureKey removedCollisionSource = StructureKey.parse("foo:a/b");
        StructureKey staleTarget = StructureKey.parse("iris:stale");
        StructureKey staleSource = StructureKey.parse("old:stale");
        StructureTransactionWriter writer = new StructureTransactionWriter(editablePack.toPath());
        assertEquals(StructureWriteResult.Status.ADDED, writer.writeManagedDatapack(
                importedBundle(collisionTarget, removedCollisionSource, "current"),
                StructureWriteMode.ADD_ONLY
        ).status());
        assertEquals(StructureWriteResult.Status.ADDED, writer.writeManagedDatapack(
                importedBundle(staleTarget, staleSource, "stale"),
                StructureWriteMode.ADD_ONLY
        ).status());
        Files.writeString(
                new File(editablePack, "objects/stale.iob").toPath(),
                "modified",
                StandardCharsets.UTF_8
        );

        String targetId = editablePack.getAbsolutePath();
        DatapackIngestService.Entry removed = entry("removed", "v1", "1", "sha-one");
        removed.importedTargets.put(targetId, "removed-revision");
        removed.importedBundles.put(targetId, Map.of(
                collisionTarget.value(), removedCollisionSource.value(),
                staleTarget.value(), staleSource.value()
        ));
        DatapackIngestService.Entry retained = entry("retained", "v1", "1", "sha-two");
        retained.structureKeys = List.of("foo:a_b");
        retained.importedTargets.put(targetId, "retained-revision");
        retained.importAttempts.put(targetId, "retained-attempt");
        retained.importedBundles.put(targetId, Map.of(collisionTarget.value(), "foo:a_b"));
        retained.structuresImported = true;
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(editablePack);

        boolean cleaned = DatapackStructureImports.cleanupRemovedImports(
                data,
                targetId,
                Set.of(retained.url),
                List.of(removed, retained),
                Map.of(retained.url, retained)
        );

        assertFalse(cleaned);
        assertEquals("modified", Files.readString(
                new File(editablePack, "objects/stale.iob").toPath(), StandardCharsets.UTF_8));
        assertFalse(retained.importedTargets.containsKey(targetId));
        assertFalse(retained.importAttempts.containsKey(targetId));
        assertFalse(retained.structuresImported);
    }

    @Test
    public void removalIgnoresAGlobalCollisionNeverImportedIntoThatTarget() throws Exception {
        File root = temporaryFolder.newFolder("nonparticipating-collision-removal-root");
        File editablePack = temporaryFolder.newFolder("nonparticipating-collision-removal-editable");
        StructureKey targetKey = StructureKey.parse("iris:foo_a_b");
        StructureKey currentSource = StructureKey.parse("foo:a/b");
        StructureTransactionWriter writer = new StructureTransactionWriter(editablePack.toPath());
        assertEquals(StructureWriteResult.Status.ADDED, writer.writeManagedDatapack(
                importedBundle(targetKey, currentSource, "current"),
                StructureWriteMode.ADD_ONLY
        ).status());

        String targetId = editablePack.getAbsolutePath();
        DatapackIngestService.Entry removed = entry("removed", "v1", "1", "sha-one");
        removed.importedTargets.put(targetId, "revision-one");
        removed.importedBundles.put(targetId, Map.of("iris:foo_a_b", "foo:a/b"));
        DatapackIngestService.Entry unrelated = entry("unrelated", "v1", "1", "sha-two");
        unrelated.structureKeys = List.of("foo:a_b");
        Files.writeString(new File(root, "manifest.json").toPath(),
                new Gson().toJson(Map.of("entries", List.of(removed, unrelated))),
                StandardCharsets.UTF_8);

        assertTrue(DatapackRemoval.removeLocked(null, removed.id, root, List.of()));

        assertFalse(Files.exists(new File(editablePack, "objects/foo_a_b.iob").toPath()));
        assertFalse(Files.exists(writer.ownershipManifestPath(targetKey)));
        assertTrue(Files.readString(new File(root, "manifest.json").toPath(), StandardCharsets.UTF_8)
                .contains("unrelated"));
    }

    @Test
    public void removalSkipsEditableBundlesNowOwnedByAnotherSource() throws Exception {
        File root = temporaryFolder.newFolder("mismatched-removal-root");
        File editablePack = temporaryFolder.newFolder("mismatched-removal-editable");
        StructureKey targetKey = StructureKey.parse("iris:owned");
        StructureKey actualSource = StructureKey.parse("other:owner");
        StructureTransactionWriter writer = new StructureTransactionWriter(editablePack.toPath());
        assertEquals(StructureWriteResult.Status.ADDED, writer.writeManagedDatapack(
                importedBundle(targetKey, actualSource, "other"),
                StructureWriteMode.ADD_ONLY
        ).status());

        DatapackIngestService.Entry removed = entry("removed", "v1", "1", "sha");
        String targetId = editablePack.getAbsolutePath();
        removed.importedTargets.put(targetId, "revision");
        removed.importedBundles.put(targetId, Map.of("iris:owned", "example:former"));
        DatapackIngestService.Entry retained = entry("retained", "v1", "1", "retained-sha");
        retained.importedTargets.put(targetId, "retained-revision");
        retained.importedBundles.put(targetId, Map.of("iris:owned", "retained:desired"));
        retained.structuresImported = true;
        Files.writeString(new File(root, "manifest.json").toPath(),
                new Gson().toJson(Map.of("entries", List.of(removed, retained))),
                StandardCharsets.UTF_8);

        assertTrue(DatapackRemoval.removeLocked(null, removed.id, root, List.of()));

        assertEquals("other", Files.readString(new File(editablePack, "objects/owned.iob").toPath()));
        assertTrue(Files.exists(writer.ownershipManifestPath(targetKey)));
        JsonObject retainedJson = JsonParser.parseString(Files.readString(
                        new File(root, "manifest.json").toPath(), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        assertFalse(retainedJson.getAsJsonObject("importedTargets").has(targetId));
        assertFalse(retainedJson.get("structuresImported").getAsBoolean());
    }

    @Test
    public void removalInvalidatesRetainedClaimsWhenTheEditableTargetIsMissing() throws Exception {
        File root = temporaryFolder.newFolder("missing-editable-removal-root");
        File missing = new File(temporaryFolder.getRoot(), "missing-editable-removal-target");
        String targetId = missing.getAbsolutePath();
        DatapackIngestService.Entry removed = entry("removed", "v1", "1", "sha");
        removed.importedTargets.put(targetId, "removed-revision");
        removed.importedBundles.put(targetId, Map.of("iris:owned", "example:former"));
        DatapackIngestService.Entry retained = entry("retained", "v1", "1", "retained-sha");
        retained.importedTargets.put(targetId, "retained-revision");
        retained.importedBundles.put(targetId, Map.of("iris:owned", "retained:desired"));
        retained.structuresImported = true;
        Files.writeString(new File(root, "manifest.json").toPath(),
                new Gson().toJson(Map.of("entries", List.of(removed, retained))),
                StandardCharsets.UTF_8);

        assertTrue(DatapackRemoval.removeLocked(null, removed.id, root, List.of()));

        assertFalse(missing.exists());
        JsonObject retainedJson = JsonParser.parseString(Files.readString(
                        new File(root, "manifest.json").toPath(), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        assertFalse(retainedJson.getAsJsonObject("importedTargets").has(targetId));
        assertFalse(retainedJson.get("structuresImported").getAsBoolean());
    }

    @Test
    public void failedUpdateCandidateCannotMutateCommittedManifestEntry() {
        DatapackIngestService.Entry committed = new DatapackIngestService.Entry();
        committed.versionId = "v1";
        committed.structureKeys = new ArrayList<>(List.of("test:old"));
        committed.importedTargets.put("pack", "old");
        committed.importAttempts.put("pack", "old-attempt");
        committed.importedBundles.put("pack", new HashMap<>(Map.of("iris:old", "test:old")));

        DatapackIngestService.Entry candidate = DatapackManifestStore.copyEntry(committed);
        candidate.versionId = "v2";
        candidate.structureKeys.add("test:new");
        candidate.importedTargets.put("pack", "new");
        candidate.importAttempts.put("pack", "new-attempt");
        candidate.importedBundles.get("pack").put("iris:new", "test:new");

        assertEquals("v1", committed.versionId);
        assertEquals(List.of("test:old"), committed.structureKeys);
        assertEquals("old", committed.importedTargets.get("pack"));
        assertEquals("old-attempt", committed.importAttempts.get("pack"));
        assertEquals(Map.of("iris:old", "test:old"), committed.importedBundles.get("pack"));
    }

    @Test
    public void publishingInstallCrashRollsEveryWorldBackToTheCommittedManifest() throws Exception {
        File root = temporaryFolder.newFolder("install-crash-rollback-root");
        File world = temporaryFolder.newFolder("install-crash-rollback-world");
        DatapackIngestService.Entry original = entry("managed", "v1", "1", "old-sha");
        DatapackIngestService.Entry desired = entry("managed", "v2", "2", "new-sha");
        writeManifest(root, original);

        File target = new File(world, desired.id);
        writeManagedDatapack(target, original, "old");
        String originalHash = ownershipHash(target);
        File scratch = new File(world.getParentFile(), ".iris-datapack-install");
        assertTrue(scratch.mkdirs());
        File pending = new File(scratch, "managed-pending");
        File backup = new File(scratch, "managed-backup");
        writeManagedDatapack(pending, desired, "new");
        String desiredHash = ownershipHash(pending);
        Files.move(target.toPath(), backup.toPath());
        Files.move(pending.toPath(), target.toPath());
        Map<String, Object> directory = installDirectory(target, pending, backup, true, originalHash, desiredHash);
        File transaction = writeCoordinator(root, "INSTALL", "PUBLISHING", desired, false,
                List.of(directory), List.of());

        DatapackScratchRecovery.recoverTransactions(root, List.of(world));

        assertEquals("old", Files.readString(new File(target, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertFalse(backup.exists());
        assertFalse(transaction.exists());
    }

    @Test
    public void publishedInstallCrashKeepsEveryWorldAfterTheManifestCommit() throws Exception {
        File root = temporaryFolder.newFolder("install-crash-commit-root");
        File world = temporaryFolder.newFolder("install-crash-commit-world");
        DatapackIngestService.Entry original = entry("managed", "v1", "1", "old-sha");
        DatapackIngestService.Entry desired = entry("managed", "v2", "2", "new-sha");
        writeManifest(root, desired);

        File target = new File(world, desired.id);
        writeManagedDatapack(target, original, "old");
        String originalHash = ownershipHash(target);
        File scratch = new File(world.getParentFile(), ".iris-datapack-install");
        assertTrue(scratch.mkdirs());
        File pending = new File(scratch, "managed-pending-commit");
        File backup = new File(scratch, "managed-backup-commit");
        writeManagedDatapack(pending, desired, "new");
        String desiredHash = ownershipHash(pending);
        Files.move(target.toPath(), backup.toPath());
        Files.move(pending.toPath(), target.toPath());
        Map<String, Object> directory = installDirectory(target, pending, backup, true, originalHash, desiredHash);
        File transaction = writeCoordinator(root, "INSTALL", "PUBLISHED", desired, false,
                List.of(directory), List.of());

        DatapackScratchRecovery.recoverTransactions(root, List.of(world));

        assertEquals("new", Files.readString(new File(target, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertFalse(backup.exists());
        assertFalse(transaction.exists());
    }

    @Test
    public void publishingInstallCrashRemovesFinderMetadataBeforeRollback() throws Exception {
        File root = temporaryFolder.newFolder("finder-install-crash-rollback-root");
        File world = temporaryFolder.newFolder("finder-install-crash-rollback-world");
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        writeManifest(root, entry);

        File target = new File(world, entry.id);
        writeManagedDatapack(target, entry, "old");
        String originalHash = ownershipHash(target);
        File scratch = new File(world.getParentFile(), ".iris-datapack-install");
        assertTrue(scratch.mkdirs());
        File pending = new File(scratch, "managed-pending-finder");
        File backup = new File(scratch, "managed-backup-finder");
        writeManagedDatapack(pending, entry, "new");
        assertTrue(new File(pending, "data/nova_structures").mkdirs());
        DatapackOwnership.writeOwnership(pending, entry);
        String desiredHash = ownershipHash(pending);
        Files.move(target.toPath(), backup.toPath());
        Files.move(pending.toPath(), target.toPath());
        Map<String, Object> directory = installDirectory(
                target, pending, backup, true, originalHash, desiredHash);
        File transaction = writeCoordinator(root, "INSTALL", "PUBLISHING", entry, true,
                List.of(directory), List.of());
        Files.writeString(new File(target, ".DS_Store").toPath(), "finder", StandardCharsets.UTF_8);
        Files.writeString(new File(target, "data/.DS_Store").toPath(), "finder", StandardCharsets.UTF_8);
        Files.writeString(new File(target, "data/nova_structures/.DS_Store").toPath(),
                "finder", StandardCharsets.UTF_8);

        DatapackScratchRecovery.recoverTransactions(root, List.of(world));

        assertEquals("old", Files.readString(new File(target, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertFalse(new File(target, ".DS_Store").exists());
        assertFalse(backup.exists());
        assertFalse(transaction.exists());
    }

    @Test
    public void publishingInstallCrashStillRejectsAuthoredContentMutation() throws Exception {
        File root = temporaryFolder.newFolder("changed-install-crash-rollback-root");
        File world = temporaryFolder.newFolder("changed-install-crash-rollback-world");
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        writeManifest(root, entry);

        File target = new File(world, entry.id);
        writeManagedDatapack(target, entry, "old");
        String originalHash = ownershipHash(target);
        File scratch = new File(world.getParentFile(), ".iris-datapack-install");
        assertTrue(scratch.mkdirs());
        File pending = new File(scratch, "managed-pending-changed");
        File backup = new File(scratch, "managed-backup-changed");
        writeManagedDatapack(pending, entry, "new");
        String desiredHash = ownershipHash(pending);
        Files.move(target.toPath(), backup.toPath());
        Files.move(pending.toPath(), target.toPath());
        Map<String, Object> directory = installDirectory(
                target, pending, backup, true, originalHash, desiredHash);
        File transaction = writeCoordinator(root, "INSTALL", "PUBLISHING", entry, true,
                List.of(directory), List.of());
        Files.writeString(new File(target, "value.txt").toPath(), "changed", StandardCharsets.UTF_8);

        try {
            DatapackScratchRecovery.recoverTransactions(root, List.of(world));
            fail("Expected authored datapack mutation to block recovery");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("content changed"));
        }

        assertEquals("changed", Files.readString(
                new File(target, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertTrue(backup.exists());
        assertTrue(transaction.exists());
    }

    @Test
    public void publishingInstallCrashRestoresManagedStagingWithEveryWorld() throws Exception {
        File root = temporaryFolder.newFolder("staging-install-crash-rollback-root");
        DatapackIngestService.Entry original = entry("managed", "v1", "1", "old-sha");
        DatapackIngestService.Entry desired = entry("managed", "v2", "2", "new-sha");
        writeManifest(root, original);

        File staging = new File(root, "staging");
        assertTrue(staging.mkdirs());
        File target = new File(staging, desired.id);
        writeManagedDatapack(target, original, "old");
        String originalHash = ownershipHash(target);
        File scratch = new File(root, ".iris-datapack-install");
        assertTrue(scratch.mkdirs());
        File pending = new File(scratch, "managed-" + UUID.randomUUID());
        File backup = new File(scratch, "managed-backup-" + UUID.randomUUID());
        writeManagedDatapack(pending, desired, "new");
        String desiredHash = ownershipHash(pending);
        Files.move(target.toPath(), backup.toPath());
        Files.move(pending.toPath(), target.toPath());
        Map<String, Object> directory = installDirectory(
                target, pending, backup, true, originalHash, desiredHash);
        File transaction = writeCoordinator(root, "INSTALL", "PUBLISHING", desired, false,
                List.of(directory), List.of());

        DatapackScratchRecovery.recoverTransactions(root, List.of());

        assertEquals("old", Files.readString(new File(target, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertFalse(backup.exists());
        assertFalse(transaction.exists());
    }

    @Test
    public void publishedInstallCrashKeepsManagedStagingAfterManifestCommit() throws Exception {
        File root = temporaryFolder.newFolder("staging-install-crash-commit-root");
        DatapackIngestService.Entry original = entry("managed", "v1", "1", "old-sha");
        DatapackIngestService.Entry desired = entry("managed", "v2", "2", "new-sha");
        writeManifest(root, desired);

        File staging = new File(root, "staging");
        assertTrue(staging.mkdirs());
        File target = new File(staging, desired.id);
        writeManagedDatapack(target, original, "old");
        String originalHash = ownershipHash(target);
        File scratch = new File(root, ".iris-datapack-install");
        assertTrue(scratch.mkdirs());
        File pending = new File(scratch, "managed-" + UUID.randomUUID());
        File backup = new File(scratch, "managed-backup-" + UUID.randomUUID());
        writeManagedDatapack(pending, desired, "new");
        String desiredHash = ownershipHash(pending);
        Files.move(target.toPath(), backup.toPath());
        Files.move(pending.toPath(), target.toPath());
        Map<String, Object> directory = installDirectory(
                target, pending, backup, true, originalHash, desiredHash);
        File transaction = writeCoordinator(root, "INSTALL", "PUBLISHED", desired, false,
                List.of(directory), List.of());

        DatapackScratchRecovery.recoverTransactions(root, List.of());

        assertEquals("new", Files.readString(new File(target, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertFalse(backup.exists());
        assertFalse(transaction.exists());
    }

    @Test
    public void publishingLegacyStagingCrashRestoresTheExactUnmarkedDirectory() throws Exception {
        File root = temporaryFolder.newFolder("legacy-staging-crash-rollback-root");
        DatapackIngestService.Entry original = entry("managed", "v1", "1", "old-sha");
        DatapackIngestService.Entry desired = entry("managed", "v2", "2", "new-sha");
        writeManifest(root, original);
        File staging = new File(root, "staging");
        assertTrue(staging.mkdirs());
        File target = new File(staging, desired.id);
        writeLegacyDatapack(target, "old");
        DatapackOwnership.writeOwnership(target, original);
        String originalHash = ownershipHash(target);
        assertTrue(new File(target, ".iris-managed.json").delete());
        File scratch = new File(root, ".iris-datapack-install");
        assertTrue(scratch.mkdirs());
        File pending = new File(scratch, "managed-" + UUID.randomUUID());
        File backup = new File(scratch, "managed-backup-" + UUID.randomUUID());
        writeManagedDatapack(pending, desired, "new");
        String desiredHash = ownershipHash(pending);
        Files.move(target.toPath(), backup.toPath());
        Files.move(pending.toPath(), target.toPath());
        Map<String, Object> directory = installDirectory(
                target, pending, backup, true, originalHash, desiredHash);
        File transaction = writeCoordinator(root, "INSTALL", "PUBLISHING", desired, false,
                List.of(directory), List.of());

        DatapackScratchRecovery.recoverTransactions(root, List.of());

        assertEquals("old", Files.readString(new File(target, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertTrue(new File(target, ".DS_Store").isFile());
        assertFalse(new File(target, ".iris-managed.json").exists());
        assertFalse(backup.exists());
        assertFalse(transaction.exists());
    }

    @Test
    public void publishedLegacyStagingCrashCommitsTheManagedDirectory() throws Exception {
        File root = temporaryFolder.newFolder("legacy-staging-crash-commit-root");
        DatapackIngestService.Entry original = entry("managed", "v1", "1", "old-sha");
        DatapackIngestService.Entry desired = entry("managed", "v2", "2", "new-sha");
        writeManifest(root, desired);
        File staging = new File(root, "staging");
        assertTrue(staging.mkdirs());
        File target = new File(staging, desired.id);
        writeLegacyDatapack(target, "old");
        DatapackOwnership.writeOwnership(target, original);
        String originalHash = ownershipHash(target);
        assertTrue(new File(target, ".iris-managed.json").delete());
        File scratch = new File(root, ".iris-datapack-install");
        assertTrue(scratch.mkdirs());
        File pending = new File(scratch, "managed-" + UUID.randomUUID());
        File backup = new File(scratch, "managed-backup-" + UUID.randomUUID());
        writeManagedDatapack(pending, desired, "new");
        String desiredHash = ownershipHash(pending);
        Files.move(target.toPath(), backup.toPath());
        Files.move(pending.toPath(), target.toPath());
        Map<String, Object> directory = installDirectory(
                target, pending, backup, true, originalHash, desiredHash);
        File transaction = writeCoordinator(root, "INSTALL", "PUBLISHED", desired, false,
                List.of(directory), List.of());

        DatapackScratchRecovery.recoverTransactions(root, List.of());

        assertEquals("new", Files.readString(new File(target, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertTrue(new File(target, ".iris-managed.json").isFile());
        assertFalse(backup.exists());
        assertFalse(transaction.exists());
    }

    @Test
    public void recoveryDeletesAnUnjournaledPreparedInstallCopy() throws Exception {
        File root = temporaryFolder.newFolder("orphan-install-pending-root");
        File scratch = new File(root, ".iris-datapack-install");
        assertTrue(scratch.mkdirs());
        File pending = new File(scratch, "managed-" + UUID.randomUUID());
        assertTrue(pending.mkdirs());
        Files.writeString(new File(pending, "partial.dat").toPath(), "partial", StandardCharsets.UTF_8);

        DatapackScratchRecovery.recoverTransactions(root, List.of());

        assertFalse(pending.exists());
        assertFalse(scratch.exists());
    }

    @Test
    public void recoveryRemovesFinderMetadataFromInstallScratch() throws Exception {
        File root = temporaryFolder.newFolder("orphan-install-finder-metadata-root");
        File scratch = new File(root, ".iris-datapack-install");
        assertTrue(scratch.mkdirs());
        File pending = new File(scratch, "managed-" + UUID.randomUUID());
        assertTrue(pending.mkdirs());
        Files.writeString(new File(pending, "partial.dat").toPath(), "partial", StandardCharsets.UTF_8);
        File metadata = new File(scratch, ".DS_Store");
        Files.writeString(metadata.toPath(), "finder", StandardCharsets.UTF_8);

        DatapackScratchRecovery.recoverTransactions(root, List.of());

        assertFalse(metadata.exists());
        assertFalse(pending.exists());
        assertFalse(scratch.exists());
    }

    @Test
    public void installScratchDeletionRetriesATransientDirectoryFailure() throws Exception {
        File root = temporaryFolder.newFolder("transient-install-scratch-delete-root");
        DeleteAttemptFile scratch = new DeleteAttemptFile(
                new File(root, "managed-" + UUID.randomUUID()).getPath(), 2);
        assertTrue(scratch.mkdir());
        Files.writeString(new File(scratch, ".DS_Store").toPath(), "finder", StandardCharsets.UTF_8);

        DatapackInstall.deleteInstallScratch(scratch, "test datapack install scratch");

        assertFalse(scratch.exists());
        assertEquals(2, scratch.deleteAttempts());
    }

    @Test
    public void installScratchDeletionStillFailsAfterBoundedRetries() throws Exception {
        File root = temporaryFolder.newFolder("persistent-install-scratch-delete-root");
        DeleteAttemptFile scratch = new DeleteAttemptFile(
                new File(root, "managed-" + UUID.randomUUID()).getPath(), Integer.MAX_VALUE);
        assertTrue(scratch.mkdir());

        try {
            DatapackInstall.deleteInstallScratch(scratch, "test datapack install scratch");
            fail("Expected persistent scratch deletion failure");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Could not remove test datapack install scratch"));
        }

        assertTrue(scratch.exists());
        assertEquals(3, scratch.deleteAttempts());
    }

    @Test
    public void recoveryRejectsFinderMetadataDirectoryInInstallScratch() throws Exception {
        File root = temporaryFolder.newFolder("orphan-install-finder-directory-root");
        File scratch = new File(root, ".iris-datapack-install");
        assertTrue(scratch.mkdirs());
        File metadata = new File(scratch, ".DS_Store");
        assertTrue(metadata.mkdirs());

        try {
            DatapackScratchRecovery.recoverTransactions(root, List.of());
            fail("Expected suspicious Finder metadata to block recovery");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Suspicious datapack recovery artifact"));
        }
        assertTrue(metadata.isDirectory());
    }

    @Test
    public void recoveryPreservesAndBlocksOnAnUnjournaledInstallBackup() throws Exception {
        File root = temporaryFolder.newFolder("orphan-install-backup-root");
        File scratch = new File(root, ".iris-datapack-install");
        assertTrue(scratch.mkdirs());
        File backup = new File(scratch, "managed-backup-" + UUID.randomUUID());
        assertTrue(backup.mkdirs());
        Files.writeString(new File(backup, "prior.dat").toPath(), "prior", StandardCharsets.UTF_8);

        try {
            DatapackScratchRecovery.recoverTransactions(root, List.of());
            fail("Expected an unjournaled backup to block recovery");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("unjournaled datapack install backup"));
        }
        assertEquals("prior", Files.readString(new File(backup, "prior.dat").toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void reapplyRecoveryFailurePreservesEvidenceAndBlocksCompilation() throws Exception {
        File root = temporaryFolder.newFolder("blocked-reapply-root");
        File scratch = new File(root, ".iris-datapack-install");
        assertTrue(scratch.mkdirs());
        File backup = new File(scratch, "managed-backup-" + UUID.randomUUID());
        assertTrue(backup.mkdirs());
        File evidence = new File(backup, "prior.dat");
        Files.writeString(evidence.toPath(), "prior", StandardCharsets.UTF_8);

        assertFalse(DatapackStagingReapply.recoverBeforeReapply(root, List.of()));

        assertEquals("prior", Files.readString(evidence.toPath(), StandardCharsets.UTF_8));
        assertTrue(backup.isDirectory());
    }

    @Test
    public void reapplyInstallFailurePreservesTheTargetAndBlocksCompilation() throws Exception {
        File root = temporaryFolder.newFolder("blocked-reapply-install-root");
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        writeManifest(root, entry);
        File stagingRoot = new File(root, "staging");
        File staging = new File(stagingRoot, entry.id);
        writeManagedDatapack(staging, entry, "staged");
        File world = temporaryFolder.newFolder("blocked-reapply-install-world");
        File target = new File(world, entry.id);
        assertTrue(target.mkdirs());
        Files.writeString(new File(target, "pack.mcmeta").toPath(), """
                {"pack":{"description":"test","pack_format":88}}
                """, StandardCharsets.UTF_8);
        File userFile = new File(target, "user-edit.txt");
        Files.writeString(userFile.toPath(), "preserve", StandardCharsets.UTF_8);
        KList<File> worlds = new KList<>();
        worlds.add(world);

        assertFalse(DatapackStagingReapply.reapplyStagedDirectories(
                root, stagingRoot, worlds, false));

        assertEquals("preserve", Files.readString(userFile.toPath(), StandardCharsets.UTF_8));
        assertTrue(staging.isDirectory());
    }

    @Test
    public void unusableCommittedStagingBlocksCompilation() throws Exception {
        File root = temporaryFolder.newFolder("blocked-corrupt-staging-root");
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        writeManifest(root, entry);
        File stagingRoot = new File(root, "staging");
        File staging = new File(stagingRoot, entry.id);
        writeManagedDatapack(staging, entry, "staged");
        Files.writeString(
                new File(staging, "corrupt.txt").toPath(),
                "corrupt",
                StandardCharsets.UTF_8
        );
        File world = temporaryFolder.newFolder("blocked-corrupt-staging-world");
        KList<File> worlds = new KList<>();
        worlds.add(world);

        assertFalse(DatapackStagingReapply.reapplyStagedDirectories(
                root, stagingRoot, worlds, false));

        assertFalse(new File(world, entry.id).exists());
        assertEquals("corrupt", Files.readString(
                new File(staging, "corrupt.txt").toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void missingCommittedStagingBlocksAbsentAndStaleWorldInstalls() throws Exception {
        File root = temporaryFolder.newFolder("blocked-missing-staging-root");
        DatapackIngestService.Entry entry = entry("managed", "v2", "2", "new-sha");
        writeManifest(root, entry);
        File stagingRoot = new File(root, "staging");
        assertTrue(stagingRoot.mkdirs());
        File absentWorld = temporaryFolder.newFolder("blocked-missing-staging-absent-world");
        File staleWorld = temporaryFolder.newFolder("blocked-missing-staging-stale-world");
        DatapackIngestService.Entry stale = entry("managed", "v1", "1", "old-sha");
        File staleTarget = new File(staleWorld, entry.id);
        writeManagedDatapack(staleTarget, stale, "old");
        KList<File> worlds = new KList<>();
        worlds.add(absentWorld);
        worlds.add(staleWorld);

        assertFalse(DatapackStagingReapply.reapplyStagedDirectories(
                root, stagingRoot, worlds, false));

        assertFalse(new File(absentWorld, entry.id).exists());
        assertEquals("old", Files.readString(
                new File(staleTarget, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertFalse(new File(stagingRoot, entry.id).exists());
    }

    @Test
    public void absentStagingRootBlocksACommittedManifest() throws Exception {
        File root = temporaryFolder.newFolder("absent-staging-committed-root");
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        writeManifest(root, entry);
        File stagingRoot = new File(root, "staging");

        DatapackIngestService.ReapplyOutcome outcome =
                DatapackStagingReapply.reapplyStagingRootOutcome(
                        root,
                        stagingRoot,
                        new KList<>(),
                        false);

        assertEquals(DatapackIngestService.ReapplyStatus.FAILED, outcome.status());
        assertFalse(outcome.succeeded());
        assertTrue(outcome.failure().orElseThrow().getMessage().contains(stagingRoot.getPath()));
    }

    @Test
    public void absentStagingRootIsAllowedForAnEmptyManifest() throws Exception {
        File root = temporaryFolder.newFolder("absent-staging-empty-root");
        writeManifest(root, null);
        File stagingRoot = new File(root, "staging");

        assertTrue(DatapackStagingReapply.reapplyStagingRoot(
                root, stagingRoot, new KList<>(), false));
    }

    @Test
    public void unsafeStagingRootsBlockReapply() throws Exception {
        File root = temporaryFolder.newFolder("unsafe-staging-root");
        writeManifest(root, null);
        File regularFile = new File(root, "staging-file");
        Files.writeString(regularFile.toPath(), "unsafe", StandardCharsets.UTF_8);
        assertFalse(DatapackStagingReapply.reapplyStagingRoot(
                root, regularFile, new KList<>(), false));

        File linkTarget = temporaryFolder.newFolder("unsafe-staging-link-target");
        Path symbolicLink = new File(root, "staging-link").toPath();
        try {
            Files.createSymbolicLink(symbolicLink, linkTarget.toPath());
        } catch (IOException | UnsupportedOperationException unavailable) {
            Assume.assumeNoException(unavailable);
        }
        assertFalse(DatapackStagingReapply.reapplyStagingRoot(
                root, symbolicLink.toFile(), new KList<>(), false));
    }

    @Test
    public void publishingRemovalCrashRestoresDirectoriesWhenTheManifestStillOwnsThem() throws Exception {
        File root = temporaryFolder.newFolder("removal-crash-rollback-root");
        File world = temporaryFolder.newFolder("removal-crash-rollback-world");
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        writeManifest(root, entry);
        File target = new File(world, entry.id);
        writeManagedDatapack(target, entry, "owned");
        String originalHash = ownershipHash(target);
        File scratch = new File(world.getParentFile(), ".iris-datapack-remove");
        assertTrue(scratch.mkdirs());
        File backup = new File(scratch, "managed-removal-backup");
        Files.move(target.toPath(), backup.toPath());
        Map<String, Object> directory = removalDirectory(target, backup, originalHash);
        File transaction = writeCoordinator(root, "REMOVE", "PUBLISHING", entry, true,
                List.of(directory), List.of());

        DatapackScratchRecovery.recoverTransactions(root, List.of(world));

        assertEquals("owned", Files.readString(new File(target, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertFalse(backup.exists());
        assertFalse(transaction.exists());
    }

    @Test
    public void publishedRemovalCrashDeletesBackupsAfterTheManifestCommit() throws Exception {
        File root = temporaryFolder.newFolder("removal-crash-commit-root");
        File world = temporaryFolder.newFolder("removal-crash-commit-world");
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        writeManifest(root, null);
        File target = new File(world, entry.id);
        writeManagedDatapack(target, entry, "owned");
        String originalHash = ownershipHash(target);
        File scratch = new File(world.getParentFile(), ".iris-datapack-remove");
        assertTrue(scratch.mkdirs());
        File backup = new File(scratch, "managed-removal-backup-commit");
        Files.move(target.toPath(), backup.toPath());
        Map<String, Object> directory = removalDirectory(target, backup, originalHash);
        File transaction = writeCoordinator(root, "REMOVE", "PUBLISHED", entry, true,
                List.of(directory), List.of());

        DatapackScratchRecovery.recoverTransactions(root, List.of(world));

        assertFalse(target.exists());
        assertFalse(backup.exists());
        assertFalse(transaction.exists());
    }

    @Test
    public void recoveryUsesTheLastPublishedJournalWhenTheNextWriteIsTorn() throws Exception {
        File root = temporaryFolder.newFolder("torn-next-root");
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        writeManifest(root, entry);
        File transaction = writeCoordinator(root, "INSTALL", "PUBLISHING", entry, true,
                List.of(), List.of());
        Files.writeString(new File(transaction, "journal.next.json").toPath(), "{torn", StandardCharsets.UTF_8);

        DatapackScratchRecovery.recoverTransactions(root, List.of());

        assertFalse(transaction.exists());
    }

    @Test
    public void recoveryDiscardsOnlyATornFirstJournalBeforeParticipantMutation() throws Exception {
        File root = temporaryFolder.newFolder("torn-first-root");
        File transactionRoot = new File(new File(root, ".iris-datapack-transactions"), UUID.randomUUID().toString());
        assertTrue(transactionRoot.mkdirs());
        Files.writeString(new File(transactionRoot, "journal.next.json").toPath(), "{torn", StandardCharsets.UTF_8);

        DatapackScratchRecovery.recoverTransactions(root, List.of());

        assertFalse(transactionRoot.exists());
    }

    @Test
    public void recoveryRejectsEditableParticipantsOutsideManifestPackRoots() throws Exception {
        File root = temporaryFolder.newFolder("editable-root-validation");
        File allowed = temporaryFolder.newFolder("editable-allowed");
        File arbitrary = temporaryFolder.newFolder("editable-arbitrary");
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        entry.importedTargets.put(allowed.getAbsolutePath(), "revision");
        entry.importedBundles.put(allowed.getAbsolutePath(), Map.of("iris:owned", "example:owned"));
        writeManifest(root, entry);
        Map<String, Object> editable = new LinkedHashMap<>();
        editable.put("packRoot", arbitrary.getAbsolutePath());
        editable.put("transactionId", UUID.randomUUID().toString());
        editable.put("claimId", UUID.randomUUID().toString());
        File transaction = writeCoordinator(root, "REMOVE", "PUBLISHING", entry, true,
                List.of(), List.of(editable));

        try {
            DatapackScratchRecovery.recoverTransactions(root, List.of());
            fail("Expected an arbitrary editable pack root to be rejected");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("editable pack root"));
        }
        assertTrue(transaction.exists());
    }

    @Test
    public void recoveryRejectsAScratchRootReplacedByASymbolicLink() throws Exception {
        File root = temporaryFolder.newFolder("scratch-link-recovery-root");
        File world = temporaryFolder.newFolder("scratch-link-recovery-world");
        DatapackIngestService.Entry original = entry("managed", "v1", "1", "old-sha");
        DatapackIngestService.Entry desired = entry("managed", "v2", "2", "new-sha");
        writeManifest(root, original);
        File target = new File(world, desired.id);
        writeManagedDatapack(target, original, "old");
        String originalHash = ownershipHash(target);
        File scratch = new File(world.getParentFile(), ".iris-datapack-install");
        assertTrue(scratch.mkdirs());
        File pending = new File(scratch, "managed-pending-link");
        File backup = new File(scratch, "managed-backup-link");
        writeManagedDatapack(pending, desired, "new");
        String desiredHash = ownershipHash(pending);
        Map<String, Object> directory = installDirectory(target, pending, backup, true, originalHash, desiredHash);
        File transaction = writeCoordinator(root, "INSTALL", "PUBLISHING", desired, false,
                List.of(directory), List.of());
        File preservedScratch = new File(world.getParentFile(), "preserved-scratch");
        Files.move(scratch.toPath(), preservedScratch.toPath());
        try {
            Files.createSymbolicLink(scratch.toPath(), preservedScratch.toPath());
        } catch (Exception e) {
            Assume.assumeNoException(e);
        }

        try {
            DatapackScratchRecovery.recoverTransactions(root, List.of(world));
            fail("Expected a replaced scratch root to be rejected");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("scratch root"));
        }
        assertEquals("old", Files.readString(new File(target, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertEquals("new", Files.readString(new File(preservedScratch, "managed-pending-link/value.txt").toPath(),
                StandardCharsets.UTF_8));
        assertTrue(transaction.exists());
    }

    @Test
    public void recoveryRejectsMalformedDirectoryParticipantPathsAsIoFailures() throws Exception {
        String invalidPath = String.valueOf((char) 0);
        for (String field : List.of("target", "targetRoot", "scratchRoot", "backup", "pending")) {
            File root = temporaryFolder.newFolder("invalid-directory-path-" + field);
            File world = temporaryFolder.newFolder(
                    "invalid-directory-container-" + field,
                    "datapacks"
            );
            DatapackIngestService.Entry original = entry("managed", "v1", "1", "old-sha");
            DatapackIngestService.Entry desired = entry("managed", "v2", "2", "new-sha");
            writeManifest(root, original);
            File target = new File(world, desired.id);
            writeManagedDatapack(target, original, "old");
            String originalHash = ownershipHash(target);
            File scratch = new File(world.getParentFile(), ".iris-datapack-install");
            assertTrue(scratch.mkdirs());
            File pending = new File(scratch, "managed-pending-" + field);
            File backup = new File(scratch, "managed-backup-" + field);
            writeManagedDatapack(pending, desired, "new");
            String desiredHash = ownershipHash(pending);
            Map<String, Object> directory = installDirectory(
                    target,
                    pending,
                    backup,
                    true,
                    originalHash,
                    desiredHash
            );
            directory.put(field, invalidPath);
            File transaction = writeCoordinator(root, "INSTALL", "PUBLISHING", desired, false,
                    List.of(directory), List.of());

            try {
                DatapackScratchRecovery.recoverTransactions(root, List.of(world));
                fail("Expected malformed " + field + " path to be rejected");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("path"));
            }
            assertEquals("old", Files.readString(new File(target, "value.txt").toPath(),
                    StandardCharsets.UTF_8));
            assertTrue(transaction.exists());
        }
    }

    @Test
    public void recoveryRejectsMalformedEditablePackRootsAsIoFailures() throws Exception {
        File root = temporaryFolder.newFolder("invalid-editable-path-root");
        File allowed = temporaryFolder.newFolder("invalid-editable-path-allowed");
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        entry.importedTargets.put(allowed.getAbsolutePath(), "revision");
        entry.importedBundles.put(allowed.getAbsolutePath(), Map.of("iris:owned", "example:owned"));
        writeManifest(root, entry);
        Map<String, Object> editable = new LinkedHashMap<>();
        editable.put("packRoot", String.valueOf((char) 0));
        editable.put("transactionId", UUID.randomUUID().toString());
        editable.put("claimId", UUID.randomUUID().toString());
        File transaction = writeCoordinator(root, "REMOVE", "PUBLISHING", entry, true,
                List.of(), List.of(editable));

        try {
            DatapackScratchRecovery.recoverTransactions(root, List.of());
            fail("Expected malformed editable pack root to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("editable pack root path"));
        }
        assertTrue(transaction.exists());
    }

    @Test
    public void genericStructureRecoveryDefersToTheDatapackCoordinator() throws Exception {
        File root = temporaryFolder.newFolder("coordinator-first-order-root");
        File editablePack = temporaryFolder.newFolder("coordinator-first-order-editable");
        StructureKey targetKey = StructureKey.parse("iris:owned");
        StructureKey sourceKey = StructureKey.parse("example:owned");
        StructureTransactionWriter writer = new StructureTransactionWriter(editablePack.toPath());
        StructureWriteResult write = writer.writeManagedDatapack(
                importedBundle(targetKey, sourceKey, "owned"),
                StructureWriteMode.ADD_ONLY
        );
        assertEquals(StructureWriteResult.Status.ADDED, write.status());
        StructureTransactionWriter.PreparedRemoval removal = writer.prepareOwnedRemovals(List.of(
                new StructureTransactionWriter.OwnedRemoval(
                        targetKey,
                        StructureSource.Kind.DATAPACK,
                        sourceKey,
                        Optional.empty(),
                        Optional.empty()
                )
        ));
        StructureTransactionWriter.PreparedRemovalToken token = removal.recoveryToken().orElseThrow();
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        entry.importedTargets.put(token.packRoot().toString(), "revision");
        entry.importedBundles.put(token.packRoot().toString(), Map.of("iris:owned", "example:owned"));
        writeManifest(root, entry);
        UUID claimId = UUID.randomUUID();
        Map<String, Object> editable = editableParticipant(token, claimId);
        File transaction = writeCoordinator(root, "REMOVE", "PUBLISHING", entry, true,
                List.of(), List.of(editable));
        UUID coordinatorId = UUID.fromString(transaction.getName());
        removal.claimRecoveryOwner(new StructureTransactionWriter.RecoveryOwner(
                transaction.toPath(), coordinatorId, claimId));
        removal.leaveForRecovery();

        StructureRecoveryResult genericRecovery = new StructureTransactionWriter(editablePack.toPath())
                .recoverIncompleteTransactions();

        assertFalse(genericRecovery.successful());
        assertFalse(Files.exists(new File(editablePack, "objects/owned.iob").toPath()));
        DatapackScratchRecovery.recoverTransactions(root, List.of());
        assertEquals("owned", Files.readString(new File(editablePack, "objects/owned.iob").toPath(),
                StandardCharsets.UTF_8));
        assertFalse(transaction.exists());
    }

    @Test
    public void datapackRecoveryCanResolveAClaimBeforeGenericStructureRecoveryRuns() throws Exception {
        File root = temporaryFolder.newFolder("datapack-first-order-root");
        File editablePack = temporaryFolder.newFolder("datapack-first-order-editable");
        StructureKey targetKey = StructureKey.parse("iris:owned");
        StructureKey sourceKey = StructureKey.parse("example:owned");
        StructureTransactionWriter writer = new StructureTransactionWriter(editablePack.toPath());
        StructureWriteResult write = writer.writeManagedDatapack(
                importedBundle(targetKey, sourceKey, "owned"),
                StructureWriteMode.ADD_ONLY
        );
        assertEquals(StructureWriteResult.Status.ADDED, write.status());
        StructureTransactionWriter.PreparedRemoval removal = writer.prepareOwnedRemovals(List.of(
                new StructureTransactionWriter.OwnedRemoval(
                        targetKey,
                        StructureSource.Kind.DATAPACK,
                        sourceKey,
                        Optional.empty(),
                        Optional.empty()
                )
        ));
        StructureTransactionWriter.PreparedRemovalToken token = removal.recoveryToken().orElseThrow();
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        entry.importedTargets.put(token.packRoot().toString(), "revision");
        entry.importedBundles.put(token.packRoot().toString(), Map.of("iris:owned", "example:owned"));
        writeManifest(root, entry);
        UUID claimId = UUID.randomUUID();
        Map<String, Object> editable = editableParticipant(token, claimId);
        File transaction = writeCoordinator(root, "REMOVE", "PUBLISHING", entry, true,
                List.of(), List.of(editable));
        UUID coordinatorId = UUID.fromString(transaction.getName());
        removal.claimRecoveryOwner(new StructureTransactionWriter.RecoveryOwner(
                transaction.toPath(), coordinatorId, claimId));
        removal.leaveForRecovery();

        DatapackScratchRecovery.recoverTransactions(root, List.of());
        StructureRecoveryResult genericRecovery = new StructureTransactionWriter(editablePack.toPath())
                .recoverIncompleteTransactions();

        assertTrue(genericRecovery.successful());
        assertEquals("owned", Files.readString(new File(editablePack, "objects/owned.iob").toPath(),
                StandardCharsets.UTF_8));
        assertFalse(transaction.exists());
    }

    @Test
    public void publishedRemovalWalAuthorizesClaimedEditableRecoveryAfterManifestCommit() throws Exception {
        EditableRecoveryFixture fixture = editableRecoveryFixture(
                "wal-committed-removal",
                "REMOVE",
                "PUBLISHED",
                false,
                true,
                true
        );

        DatapackScratchRecovery.recoverTransactions(fixture.root(), List.of());

        assertFalse(fixture.coordinator().exists());
        assertFalse(Files.exists(new File(fixture.editablePack(), "objects/owned.iob").toPath()));
        assertTrue(new StructureTransactionWriter(fixture.editablePack().toPath())
                .recoverIncompleteTransactions().successful());
    }

    @Test
    public void preparedRemovalWalCannotAuthorizeEditableRecoveryWithoutTheManifest() throws Exception {
        EditableRecoveryFixture fixture = editableRecoveryFixture(
                "wal-prepared-removal",
                "REMOVE",
                "PREPARED",
                false,
                true,
                true
        );

        assertRecoveryRejected(fixture, "authority");
    }

    @Test
    public void installWalCannotAuthorizeAnEditableRemoval() throws Exception {
        EditableRecoveryFixture fixture = editableRecoveryFixture(
                "wal-install",
                "INSTALL",
                "PUBLISHED",
                false,
                true,
                true
        );

        assertRecoveryRejected(fixture, "editable participants");
    }

    @Test
    public void unclaimedPublishedRemovalWalCannotAuthorizeEditableRecovery() throws Exception {
        EditableRecoveryFixture fixture = editableRecoveryFixture(
                "wal-unclaimed-removal",
                "REMOVE",
                "PUBLISHED",
                false,
                false,
                true
        );

        assertRecoveryRejected(fixture, "recovery claim");
    }

    @Test
    public void mismatchedPublishedRemovalClaimCannotAuthorizeEditableRecovery() throws Exception {
        EditableRecoveryFixture fixture = editableRecoveryFixture(
                "wal-mismatched-removal",
                "REMOVE",
                "PUBLISHED",
                false,
                true,
                false
        );

        assertRecoveryRejected(fixture, "does not match");
    }

    @Test
    public void changedPreparedRemovalBackupCannotGainWalAuthority() throws Exception {
        EditableRecoveryFixture fixture = editableRecoveryFixture(
                "wal-changed-removal",
                "REMOVE",
                "PUBLISHED",
                false,
                true,
                true
        );
        Files.writeString(fixture.backup(), "changed", StandardCharsets.UTF_8);

        assertRecoveryRejected(fixture, "hash mismatch");
    }

    @Test
    public void recoveryBoundsTransactionCountBeforeLoadingAnyJournal() throws Exception {
        File root = temporaryFolder.newFolder("transaction-count-bound");
        File transactions = new File(root, ".iris-datapack-transactions");
        assertTrue(transactions.mkdirs());
        File first = null;
        for (int i = 0; i < 1_025; i++) {
            File transaction = new File(transactions, UUID.randomUUID().toString());
            assertTrue(transaction.mkdir());
            if (first == null) {
                first = transaction;
            }
        }

        try {
            DatapackScratchRecovery.recoverTransactions(root, List.of());
            fail("Expected excessive transaction count to be rejected");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("transaction count"));
        }
        assertTrue(first != null && first.exists());
    }

    @Test
    public void recoveryRemovesHarmlessFinderMetadata() throws Exception {
        File root = temporaryFolder.newFolder("transaction-finder-metadata");
        File transactions = new File(root, ".iris-datapack-transactions");
        assertTrue(transactions.mkdirs());
        File metadata = new File(transactions, ".DS_Store");
        Files.writeString(metadata.toPath(), "finder", StandardCharsets.UTF_8);

        assertTrue(DatapackScratchRecovery.recoverTransactions(root, List.of()));

        assertFalse(metadata.exists());
    }

    @Test
    public void recoveryReportsUnchangedWhenNoRecoveryArtifactsExist() throws Exception {
        File root = temporaryFolder.newFolder("unchanged-recovery-root");

        assertFalse(DatapackScratchRecovery.recoverTransactions(root, List.of()));
    }

    @Test
    public void recoveryDeletesOnlyExactlyNamedPendingStagingScratch() throws Exception {
        File root = temporaryFolder.newFolder("pending-staging-recovery-root");
        File staging = new File(root, "staging");
        assertTrue(staging.mkdirs());
        File pending = new File(staging, ".pending-managed-" + UUID.randomUUID());
        assertTrue(pending.mkdirs());
        Files.writeString(new File(pending, ".iris-extract-part.tmp").toPath(), "partial",
                StandardCharsets.UTF_8);
        File lookalike = new File(staging, ".pending-managed-not-a-uuid");
        assertTrue(lookalike.mkdirs());

        DatapackScratchRecovery.recoverTransactions(root, List.of());

        assertFalse(pending.exists());
        assertTrue(lookalike.isDirectory());
    }

    @Test
    public void recoveryRefusesExactlyNamedSymbolicLinkStagingScratch() throws Exception {
        File root = temporaryFolder.newFolder("linked-staging-recovery-root");
        File staging = new File(root, "staging");
        assertTrue(staging.mkdirs());
        File outside = temporaryFolder.newFolder("linked-staging-recovery-outside");
        File linked = new File(staging, ".pending-managed-" + UUID.randomUUID());
        try {
            Files.createSymbolicLink(linked.toPath(), outside.toPath());
        } catch (Exception e) {
            Assume.assumeNoException(e);
        }

        try {
            DatapackScratchRecovery.recoverTransactions(root, List.of());
            fail("Expected symbolic-link staging scratch to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("staging scratch artifact"));
        }
        assertTrue(Files.isSymbolicLink(linked.toPath()));
        assertTrue(outside.isDirectory());
    }

    @Test
    public void recoveryRefusesSpecialFilesInsidePendingStagingScratch() throws Exception {
        Path temporaryBase = Path.of("/tmp");
        Assume.assumeTrue(Files.isDirectory(temporaryBase));
        File root = Files.createTempDirectory(temporaryBase, "iris-s-").toFile();
        File staging = new File(root, "staging");
        assertTrue(staging.mkdirs());
        File pending = new File(staging, ".pending-m-" + UUID.randomUUID());
        assertTrue(pending.mkdirs());
        Path socket = new File(pending, "s").toPath();
        try {
            try (ServerSocketChannel channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
                channel.bind(UnixDomainSocketAddress.of(socket));
                try {
                    DatapackScratchRecovery.recoverTransactions(root, List.of());
                    fail("Expected special staging scratch file to be rejected");
                } catch (IOException expected) {
                    assertTrue(expected.getMessage().contains("unsupported file"));
                }
            }
            assertTrue(pending.isDirectory());
        } catch (UnsupportedOperationException e) {
            Assume.assumeNoException(e);
        } finally {
            Files.deleteIfExists(socket);
            Files.deleteIfExists(pending.toPath());
            Files.deleteIfExists(staging.toPath());
            Files.deleteIfExists(root.toPath());
        }
    }

    @Test
    public void recoveryRestoresTheSoleVerifiedStagingBackupWhenTargetIsMissing() throws Exception {
        File root = temporaryFolder.newFolder("staging-backup-restore-root");
        File staging = new File(root, "staging");
        assertTrue(staging.mkdirs());
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        File target = new File(staging, entry.id);
        writeManagedDatapack(target, entry, "old");
        File backup = new File(staging, ".backup-managed-" + UUID.randomUUID());
        Files.move(target.toPath(), backup.toPath());

        DatapackScratchRecovery.recoverTransactions(root, List.of());

        assertEquals("old", Files.readString(new File(target, "value.txt").toPath(),
                StandardCharsets.UTF_8));
        assertFalse(backup.exists());
    }

    @Test
    public void recoveryRetiresVerifiedStagingBackupBesideAValidTarget() throws Exception {
        File root = temporaryFolder.newFolder("staging-backup-retire-root");
        File staging = new File(root, "staging");
        assertTrue(staging.mkdirs());
        DatapackIngestService.Entry entry = entry("managed", "v2", "2", "sha");
        File target = new File(staging, entry.id);
        File backup = new File(staging, ".backup-managed-" + UUID.randomUUID());
        writeManagedDatapack(target, entry, "new");
        writeManagedDatapack(backup, entry, "old");

        DatapackScratchRecovery.recoverTransactions(root, List.of());

        assertEquals("new", Files.readString(new File(target, "value.txt").toPath(),
                StandardCharsets.UTF_8));
        assertFalse(backup.exists());
    }

    @Test
    public void recoveryPreservesAmbiguousStagingBackups() throws Exception {
        File root = temporaryFolder.newFolder("staging-backup-ambiguous-root");
        File staging = new File(root, "staging");
        assertTrue(staging.mkdirs());
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        File first = new File(staging, ".backup-managed-" + UUID.randomUUID());
        File second = new File(staging, ".backup-managed-" + UUID.randomUUID());
        writeManagedDatapack(first, entry, "first");
        writeManagedDatapack(second, entry, "second");

        try {
            DatapackScratchRecovery.recoverTransactions(root, List.of());
            fail("Expected ambiguous staging backups to be preserved");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Ambiguous"));
        }
        assertTrue(first.isDirectory());
        assertTrue(second.isDirectory());
        assertFalse(new File(staging, entry.id).exists());
    }

    @Test
    public void reapplyRecordsStagingAndInstallMetadataForTheNextPass() throws Exception {
        ReapplyFixture fixture = reapplyFixture("reapply-record");

        assertTrue(DatapackStagingReapply.reapplyStagedDirectories(
                fixture.root(), fixture.stagingRoot(), fixture.worlds(), false));

        assertTrue(new File(fixture.target(), ".iris-managed.json").isFile());
        JsonObject recorded = manifestEntry(fixture.root());
        assertFalse(recorded.get("stagingMetadata").getAsString().isBlank());
        assertTrue(recorded.getAsJsonObject("installMetadata")
                .has(fixture.target().toPath().toAbsolutePath().normalize().toString()));
    }

    @Test
    public void successfulUnchangedIngestKeepsStartupFingerprintStableAcrossNextReapply() throws Exception {
        ReapplyFixture fixture = reapplyFixture("reapply-ingest-cache");
        assertTrue(DatapackStagingReapply.reapplyStagedDirectories(
                fixture.root(), fixture.stagingRoot(), fixture.worlds(), false));
        DatapackIngestService.Entry entry = new Gson().fromJson(
                manifestEntry(fixture.root()), DatapackIngestService.Entry.class);
        DatapackIngestService.Report report = new DatapackIngestService.Report();

        DatapackStagingGuard.recordInstallResult(
                null,
                report,
                fixture.staging(),
                fixture.worlds(),
                entry,
                new InstallResult(false),
                entry.versionNumber
        );
        writePrettyManifest(fixture.root(), entry);
        String cachedFingerprint = DatapackStartupValidation.startupValidationFingerprint(
                fixture.root(), fixture.worlds());

        assertFalse(entry.stagingMetadata.isBlank());
        assertEquals(1, entry.installMetadata.size());
        assertEquals(1, report.getUpToDate().size());
        assertTrue(DatapackStagingReapply.reapplyStagedDirectories(
                fixture.root(), fixture.stagingRoot(), fixture.worlds(), false));
        assertEquals(cachedFingerprint, DatapackStartupValidation.startupValidationFingerprint(
                fixture.root(), fixture.worlds()));
    }

    @Test
    public void reapplyOutcomeDistinguishesRepairFromAnUnchangedPass() throws Exception {
        ReapplyFixture fixture = reapplyFixture("reapply-outcome");

        DatapackIngestService.ReapplyOutcome repaired =
                DatapackStagingReapply.reapplyStagingRootOutcome(
                        fixture.root(),
                        fixture.stagingRoot(),
                        fixture.worlds(),
                        false);
        DatapackIngestService.ReapplyOutcome unchanged =
                DatapackStagingReapply.reapplyStagingRootOutcome(
                        fixture.root(),
                        fixture.stagingRoot(),
                        fixture.worlds(),
                        false);

        assertEquals(DatapackIngestService.ReapplyStatus.REPAIRED, repaired.status());
        assertTrue(repaired.succeeded());
        assertTrue(repaired.changed());
        assertEquals(DatapackIngestService.ReapplyStatus.UNCHANGED, unchanged.status());
        assertTrue(unchanged.succeeded());
        assertFalse(unchanged.changed());
    }

    @Test
    public void unchangedStagingAndTargetSkipContentHashingOnReapply() throws Exception {
        ReapplyFixture fixture = reapplyFixture("reapply-shortcircuit");
        assertTrue(DatapackStagingReapply.reapplyStagedDirectories(
                fixture.root(), fixture.stagingRoot(), fixture.worlds(), false));

        Path staged = fixture.staging().toPath().resolve("value.txt");
        FileTime stamp = Files.getLastModifiedTime(staged);
        Files.writeString(staged, "wxyz", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(staged, stamp);

        assertTrue(DatapackStagingReapply.reapplyStagedDirectories(
                fixture.root(), fixture.stagingRoot(), fixture.worlds(), false));

        assertEquals("abcd", Files.readString(
                new File(fixture.target(), "value.txt").toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void changedStagingMetadataForcesFullReapplyVerification() throws Exception {
        ReapplyFixture fixture = reapplyFixture("reapply-staging-change");
        assertTrue(DatapackStagingReapply.reapplyStagedDirectories(
                fixture.root(), fixture.stagingRoot(), fixture.worlds(), false));

        Path staged = fixture.staging().toPath().resolve("value.txt");
        FileTime stamp = Files.getLastModifiedTime(staged);
        Files.writeString(staged, "wxyz", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(staged, FileTime.fromMillis(stamp.toMillis() + 5000L));

        assertFalse(DatapackStagingReapply.reapplyStagedDirectories(
                fixture.root(), fixture.stagingRoot(), fixture.worlds(), false));
    }

    @Test
    public void changedInstallTargetIsRepairedDespiteRecordedMetadata() throws Exception {
        ReapplyFixture fixture = reapplyFixture("reapply-target-change");
        assertTrue(DatapackStagingReapply.reapplyStagedDirectories(
                fixture.root(), fixture.stagingRoot(), fixture.worlds(), false));

        Path installed = fixture.target().toPath().resolve("value.txt");
        Files.writeString(installed, "zzzz", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(installed, FileTime.fromMillis(
                Files.getLastModifiedTime(installed).toMillis() + 5000L));

        assertTrue(DatapackStagingReapply.reapplyStagedDirectories(
                fixture.root(), fixture.stagingRoot(), fixture.worlds(), false));

        assertEquals("abcd", Files.readString(installed, StandardCharsets.UTF_8));
    }

    @Test
    public void flippedOverrideStrippingForcesFullReapplyVerification() throws Exception {
        ReapplyFixture fixture = reapplyFixture("reapply-strip-change");
        assertTrue(DatapackStagingReapply.reapplyStagedDirectories(
                fixture.root(), fixture.stagingRoot(), fixture.worlds(), false));
        assertFalse(new File(fixture.target(), ".iris-overrides-stripped").exists());

        assertTrue(DatapackStagingReapply.reapplyStagedDirectories(
                fixture.root(), fixture.stagingRoot(), fixture.worlds(), true));

        assertTrue(new File(fixture.target(), ".iris-overrides-stripped").isFile());
    }

    private ReapplyFixture reapplyFixture(String name) throws Exception {
        File root = temporaryFolder.newFolder(name + "-root");
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        File stagingRoot = new File(root, "staging");
        File staging = new File(stagingRoot, entry.id);
        writeManagedDatapack(staging, entry, "abcd");
        writeManifest(root, entry);
        File world = temporaryFolder.newFolder(name + "-world");
        KList<File> worlds = new KList<>();
        worlds.add(world);
        return new ReapplyFixture(root, stagingRoot, staging, worlds, new File(world, entry.id));
    }

    private PreparedMixedInstall preparedMixedInstall(String name) throws Exception {
        File root = temporaryFolder.newFolder(name + "-root").toPath().toRealPath().toFile();
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        writeManifest(root, entry);
        File staging = new File(root, "staging/" + entry.id);
        writeManagedDatapack(staging, entry, "new");

        File unchangedRoot = temporaryFolder.newFolder(name + "-unchanged-root");
        File unchangedWorld = new File(unchangedRoot, "datapacks");
        assertTrue(unchangedWorld.mkdir());
        File unchangedTarget = new File(unchangedWorld, entry.id);
        writeManagedDatapack(unchangedTarget, entry, "new");

        File changedRoot = temporaryFolder.newFolder(name + "-changed-root");
        File changedWorld = new File(changedRoot, "datapacks");
        assertTrue(changedWorld.mkdir());
        File changedTarget = new File(changedWorld, entry.id);
        writeManagedDatapack(changedTarget, entry, "old");

        KList<File> worlds = new KList<>();
        worlds.add(unchangedWorld);
        worlds.add(changedWorld);
        InstallExecution execution =
                DatapackInstall.prepareInstallExecution(staging, worlds, entry, false, root);

        assertTrue(execution.result().changed());
        assertEquals("new", Files.readString(
                new File(changedTarget, "value.txt").toPath(), StandardCharsets.UTF_8));
        return new PreparedMixedInstall(staging, unchangedTarget, changedTarget, execution);
    }

    private PreparedVerifiedFreshInstall preparedVerifiedFreshInstall(String name) throws Exception {
        LegacyStagingFixture fixture = legacyStagingFixture(name, false, true, false);
        File world = temporaryFolder.newFolder(name + "-world");
        KList<File> worlds = new KList<>();
        worlds.add(world);
        InstallExecution execution =
                DatapackInstall.prepareInstallExecution(
                        fixture.source(),
                        worlds,
                        fixture.desired(),
                        false,
                        fixture.root(),
                        fixture.authorization());
        File worldTarget = new File(world, fixture.desired().id);

        assertTrue(fixture.source().isDirectory());
        assertEquals("new", Files.readString(
                new File(worldTarget, "value.txt").toPath(), StandardCharsets.UTF_8));
        assertEquals("new", Files.readString(
                new File(fixture.target(), "value.txt").toPath(), StandardCharsets.UTF_8));
        return new PreparedVerifiedFreshInstall(
                fixture.root(),
                fixture.source(),
                fixture.desired(),
                fixture.target(),
                worldTarget,
                execution
        );
    }

    private JsonObject manifestEntry(File root) throws Exception {
        JsonObject manifest = JsonParser.parseString(Files.readString(
                new File(root, "manifest.json").toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
        return manifest.getAsJsonArray("entries").get(0).getAsJsonObject();
    }

    private record ReapplyFixture(
            File root,
            File stagingRoot,
            File staging,
            KList<File> worlds,
            File target
    ) {
    }

    private record PreparedMixedInstall(
            File staging,
            File unchangedTarget,
            File changedTarget,
            InstallExecution execution
    ) {
    }

    private record PreparedVerifiedFreshInstall(
            File root,
            File extractedDir,
            DatapackIngestService.Entry entry,
            File canonicalTarget,
            File worldTarget,
            InstallExecution execution
    ) {
    }

    private LegacyStagingFixture legacyStagingFixture(
            String name,
            boolean committed,
            boolean matchingUrl,
            boolean legacyTarget
    ) throws Exception {
        return legacyStagingFixture(name, committed, matchingUrl, legacyTarget, false);
    }

    private LegacyStagingFixture legacyStagingFixture(
            String name,
            boolean committed,
            boolean matchingUrl,
            boolean legacyTarget,
            boolean sameMetadata
    ) throws Exception {
        File root = temporaryFolder.newFolder(name).toPath().toRealPath().toFile();
        File stagingRoot = new File(root, "staging");
        assertTrue(stagingRoot.mkdir());
        DatapackIngestService.Entry desired = sameMetadata
                ? entry("managed", "v1", "1", "old-sha")
                : entry("managed", "v2", "2", "new-sha");
        if (committed) {
            DatapackIngestService.Entry prior = entry("managed", "v1", "1", "old-sha");
            if (!matchingUrl) {
                prior.url = "https://example.test/different-owner.zip";
            }
            writeManifest(root, prior);
        } else {
            writeManifest(root, null);
        }
        File target = new File(stagingRoot, desired.id);
        if (legacyTarget) {
            writeLegacyDatapack(target, sameMetadata ? "same" : "old");
        }
        File source = new File(stagingRoot, ".pending-" + desired.id + "-" + UUID.randomUUID());
        writeManagedDatapack(source, desired, sameMetadata ? "same" : "new");
        VerifiedStagingInstall authorization =
                DatapackStagingGuard.authorizeVerifiedStagingInstall(root, stagingRoot, source, desired);
        return new LegacyStagingFixture(
                root, stagingRoot, target, source, desired, ownershipHash(source), authorization);
    }

    private void writeLegacyDatapack(File directory, String value) throws Exception {
        assertTrue(directory.mkdirs());
        Files.writeString(new File(directory, "pack.mcmeta").toPath(), """
                {"pack":{"description":"test","pack_format":88}}
                """, StandardCharsets.UTF_8);
        Files.writeString(new File(directory, "value.txt").toPath(), value, StandardCharsets.UTF_8);
        Files.writeString(new File(directory, ".DS_Store").toPath(), "legacy", StandardCharsets.UTF_8);
    }

    private InstallPlan prepareLegacyStagingPlan(
            LegacyStagingFixture fixture
    ) throws Exception {
        return DatapackInstallPlanner.prepareInstall(
                fixture.source(),
                fixture.stagingRoot(),
                fixture.desired(),
                fixture.sourceHash(),
                false,
                fixture.authorization()
        );
    }

    private void assertLegacyStagingPreparationRejected(
            LegacyStagingFixture fixture,
            String expectedMessage
    ) throws Exception {
        try {
            prepareLegacyStagingPlan(fixture);
            fail("Expected legacy staging preparation to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(expectedMessage));
        }
    }

    private void assertInstallPublicationRejected(
            InstallPlan plan,
            String expectedMessage
    ) throws Exception {
        try {
            DatapackInstallPlanner.publishInstallPlan(plan);
            fail("Expected changed install participant to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(expectedMessage));
        }
    }

    private File datapackDirectory(String name) throws Exception {
        File directory = temporaryFolder.newFolder(name);
        Files.writeString(new File(directory, "pack.mcmeta").toPath(), """
                {"pack":{"description":"test","pack_format":88}}
                """, StandardCharsets.UTF_8);
        return directory;
    }

    private void writeManagedDatapack(File directory, DatapackIngestService.Entry entry) throws Exception {
        assertTrue(directory.mkdirs());
        Files.writeString(new File(directory, "pack.mcmeta").toPath(), """
                {"pack":{"description":"test","pack_format":88}}
                """, StandardCharsets.UTF_8);
        DatapackOwnership.writeOwnership(directory, entry);
    }

    private void writeManagedDatapack(
            File directory,
            DatapackIngestService.Entry entry,
            String value
    ) throws Exception {
        assertTrue(directory.mkdirs());
        Files.writeString(new File(directory, "pack.mcmeta").toPath(), """
                {"pack":{"description":"test","pack_format":88}}
                """, StandardCharsets.UTF_8);
        Files.writeString(new File(directory, "value.txt").toPath(), value, StandardCharsets.UTF_8);
        DatapackOwnership.writeOwnership(directory, entry);
    }

    private DatapackIngestService.Entry entry(
            String id,
            String versionId,
            String versionNumber,
            String sha1
    ) {
        DatapackIngestService.Entry entry = new DatapackIngestService.Entry();
        entry.id = id;
        entry.url = "https://example.test/" + id + ".zip";
        entry.versionId = versionId;
        entry.versionNumber = versionNumber;
        entry.sha1 = sha1;
        return entry;
    }

    private void writeManifest(File root, DatapackIngestService.Entry entry) throws Exception {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("entries", entry == null ? List.of() : List.of(entry));
        Files.writeString(new File(root, "manifest.json").toPath(), new Gson().toJson(manifest),
                StandardCharsets.UTF_8);
    }

    private void writePrettyManifest(File root, DatapackIngestService.Entry entry) throws Exception {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("entries", List.of(entry));
        Files.writeString(
                new File(root, "manifest.json").toPath(),
                new GsonBuilder().setPrettyPrinting().create().toJson(manifest),
                StandardCharsets.UTF_8
        );
    }

    private String ownershipHash(File directory) throws Exception {
        String marker = Files.readString(new File(directory, ".iris-managed.json").toPath(), StandardCharsets.UTF_8);
        return JsonParser.parseString(marker).getAsJsonObject().get("contentHash").getAsString();
    }

    private Map<String, Object> installDirectory(
            File target,
            File pending,
            File backup,
            boolean hadTarget,
            String originalHash,
            String desiredHash
    ) throws Exception {
        Map<String, Object> directory = new LinkedHashMap<>();
        directory.put("target", target.getAbsolutePath());
        directory.put("pending", pending.getAbsolutePath());
        directory.put("backup", backup.getAbsolutePath());
        directory.put("hadTarget", hadTarget);
        directory.put("originalHash", originalHash);
        directory.put("desiredHash", desiredHash);
        File original = backup.isDirectory() ? backup : target;
        File desired = pending.isDirectory() ? pending : target;
        directory.put("originalMarkerHash", hadTarget
                ? DatapackOwnership.ownershipMarkerFingerprint(original) : "absent");
        directory.put("desiredMarkerHash", DatapackOwnership.ownershipMarkerFingerprint(desired));
        directory.put("originalIdentity", hadTarget
                ? DatapackSupport.directoryIdentity(original) : "");
        directory.put("desiredIdentity", DatapackSupport.directoryIdentity(desired));
        directory.put("targetRoot", target.getParentFile().toPath().toRealPath().toString());
        directory.put("scratchRoot", backup.getParentFile().toPath().toRealPath().toString());
        directory.put("targetRootIdentity", DatapackSupport.directoryIdentity(target.getParentFile()));
        directory.put("scratchRootIdentity", DatapackSupport.directoryIdentity(backup.getParentFile()));
        return directory;
    }

    private Map<String, Object> removalDirectory(File target, File backup, String originalHash) throws Exception {
        Map<String, Object> directory = new LinkedHashMap<>();
        directory.put("target", target.getAbsolutePath());
        directory.put("pending", "");
        directory.put("backup", backup.getAbsolutePath());
        directory.put("hadTarget", true);
        directory.put("originalHash", originalHash);
        directory.put("desiredHash", "");
        File original = backup.isDirectory() ? backup : target;
        directory.put("originalMarkerHash", DatapackOwnership.ownershipMarkerFingerprint(original));
        directory.put("desiredMarkerHash", "");
        directory.put("originalIdentity", DatapackSupport.directoryIdentity(original));
        directory.put("desiredIdentity", "");
        directory.put("targetRoot", target.getParentFile().toPath().toRealPath().toString());
        directory.put("scratchRoot", backup.getParentFile().toPath().toRealPath().toString());
        directory.put("targetRootIdentity", DatapackSupport.directoryIdentity(target.getParentFile()));
        directory.put("scratchRootIdentity", DatapackSupport.directoryIdentity(backup.getParentFile()));
        return directory;
    }

    private Map<String, Object> editableParticipant(
            StructureTransactionWriter.PreparedRemovalToken token,
            UUID claimId
    ) {
        Map<String, Object> editable = new LinkedHashMap<>();
        editable.put("packRoot", token.packRoot().toString());
        editable.put("transactionId", token.transactionId().toString());
        editable.put("claimId", claimId.toString());
        return editable;
    }

    private File writeCoordinator(
            File root,
            String operation,
            String phase,
            DatapackIngestService.Entry entry,
            boolean manifestAlreadyMatched,
            List<Map<String, Object>> directories,
            List<Map<String, Object>> editables
    ) throws Exception {
        UUID transactionId = UUID.randomUUID();
        File transaction = new File(new File(root, ".iris-datapack-transactions"), transactionId.toString());
        assertTrue(transaction.mkdirs());
        Map<String, Object> journal = new LinkedHashMap<>();
        journal.put("schemaVersion", 2);
        journal.put("transactionId", transactionId.toString());
        journal.put("operation", operation);
        journal.put("phase", phase);
        journal.put("id", entry.id);
        journal.put("url", entry.url);
        journal.put("versionId", entry.versionId);
        journal.put("versionNumber", entry.versionNumber);
        journal.put("sha1", entry.sha1);
        journal.put("manifestAlreadyMatched", manifestAlreadyMatched);
        journal.put("directories", directories);
        journal.put("editables", editables);
        Files.writeString(new File(transaction, "journal.json").toPath(), new Gson().toJson(journal),
                StandardCharsets.UTF_8);
        return transaction;
    }

    private StructureResourceBundle importedBundle(
            StructureKey targetKey,
            StructureKey sourceKey,
            String content
    ) {
        return StructureResourceBundle.builder(targetKey)
                .source(StructureSource.of(StructureSource.Kind.DATAPACK, sourceKey))
                .backend(StructureBackend.IRIS_ASSEMBLY)
                .capability(StructureCapability.BLOCKS)
                .resource("objects/" + targetKey.path() + ".iob", content.getBytes(StandardCharsets.UTF_8))
                .textResource("structures/" + targetKey.path() + ".json", content)
                .build();
    }

    private EditableRecoveryFixture editableRecoveryFixture(
            String name,
            String operation,
            String phase,
            boolean manifestPresent,
            boolean writeClaim,
            boolean matchingClaim
    ) throws Exception {
        File root = temporaryFolder.newFolder(name + "-root");
        File editablePack = temporaryFolder.newFolder(name + "-editable");
        StructureKey targetKey = StructureKey.parse("iris:owned");
        StructureKey sourceKey = StructureKey.parse("example:owned");
        StructureTransactionWriter writer = new StructureTransactionWriter(editablePack.toPath());
        StructureWriteResult write = writer.writeManagedDatapack(
                importedBundle(targetKey, sourceKey, "owned"),
                StructureWriteMode.ADD_ONLY
        );
        assertEquals(StructureWriteResult.Status.ADDED, write.status());
        StructureTransactionWriter.PreparedRemoval removal = writer.prepareOwnedRemovals(List.of(
                new StructureTransactionWriter.OwnedRemoval(
                        targetKey,
                        StructureSource.Kind.DATAPACK,
                        sourceKey,
                        Optional.empty(),
                        Optional.empty()
                )
        ));
        StructureTransactionWriter.PreparedRemovalToken token = removal.recoveryToken().orElseThrow();
        DatapackIngestService.Entry entry = entry("managed", "v1", "1", "sha");
        entry.importedTargets.put(token.packRoot().toString(), "revision");
        entry.importedBundles.put(token.packRoot().toString(), Map.of("iris:owned", "example:owned"));
        writeManifest(root, manifestPresent ? entry : null);
        UUID journalClaimId = UUID.randomUUID();
        Map<String, Object> editable = editableParticipant(token, journalClaimId);
        File coordinator = writeCoordinator(root, operation, phase, entry, true,
                List.of(), List.of(editable));
        if (writeClaim) {
            UUID actualClaimId = matchingClaim ? journalClaimId : UUID.randomUUID();
            removal.claimRecoveryOwner(new StructureTransactionWriter.RecoveryOwner(
                    coordinator.toPath(),
                    UUID.fromString(coordinator.getName()),
                    actualClaimId
            ));
        }
        removal.leaveForRecovery();
        Path backup = new File(
                editablePack,
                ".iris/structure-staging/" + token.transactionId() + "/backup/objects/owned.iob"
        ).toPath();
        return new EditableRecoveryFixture(root, editablePack, coordinator, backup);
    }

    private void assertRecoveryRejected(EditableRecoveryFixture fixture, String expectedMessage) throws Exception {
        try {
            DatapackScratchRecovery.recoverTransactions(fixture.root(), List.of());
            fail("Expected editable recovery to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains(expectedMessage));
        }
        assertTrue(fixture.coordinator().exists());
        assertTrue(Files.exists(fixture.backup()));
        assertFalse(Files.exists(new File(fixture.editablePack(), "objects/owned.iob").toPath()));
    }

    private record EditableRecoveryFixture(
            File root,
            File editablePack,
            File coordinator,
            Path backup
    ) {
    }

    private record LegacyStagingFixture(
            File root,
            File stagingRoot,
            File target,
            File source,
            DatapackIngestService.Entry desired,
            String sourceHash,
            VerifiedStagingInstall authorization
    ) {
    }

    private static final class DeleteAttemptFile extends File {
        private final int successfulAttempt;
        private int deleteAttempts;

        private DeleteAttemptFile(String pathname, int successfulAttempt) {
            super(pathname);
            this.successfulAttempt = successfulAttempt;
        }

        @Override
        public boolean delete() {
            deleteAttempts++;
            return deleteAttempts >= successfulAttempt && super.delete();
        }

        private int deleteAttempts() {
            return deleteAttempts;
        }
    }
}
