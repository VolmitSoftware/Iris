package art.arcane.iris.pack.datapack;

import art.arcane.iris.pack.datapack.DatapackIngestService.Entry;
import art.arcane.iris.pack.datapack.ModrinthResolver.ResolvedDatapack;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.io.IO;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class DatapackExportArchives {
    private static final String DIRECT_VERSION_PREFIX = "direct-";

    private DatapackExportArchives() {
    }

    public static Path resolve(String source, DataVersion target) throws IOException {
        String normalized = DatapackImportSources.normalizeConfiguredSource(Objects.requireNonNull(source, "datapack import source"));
        URI uri = DatapackStartupValidation.parseSourceUri(normalized);
        if (uri == null) {
            throw new IOException("Empty datapack import source");
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return DatapackArchive.requireLocalDatapackPath(uri);
        }
        IrisPlatform platform = IrisPlatforms.get();
        File root = platform.dataFolder("datapacks");
        File cacheDir = new File(root, "cache");
        boolean running = DataVersion.forMinecraftVersion(platform.minecraftVersion()) == target;
        DatapackTransactions.TRANSACTION_LOCK.lock();
        try {
            Entry entry = manifestEntry(root, normalized);
            Path cached = ingestCache(cacheDir, entry, running);
            if (cached != null) {
                return cached;
            }
            return download(cacheDir, normalized, entry, target);
        } finally {
            DatapackTransactions.TRANSACTION_LOCK.unlock();
        }
    }

    private static Entry manifestEntry(File root, String source) {
        try {
            return DatapackManifestStore.readCommittedManifest(root).find(source);
        } catch (IOException exception) {
            IrisLogging.reportError("The datapack manifest under " + root.getPath() + " could not be read; '" + source
                    + "' is resolved again for the export", exception);
            return null;
        }
    }

    private static Path ingestCache(File cacheDir, Entry entry, boolean running) throws IOException {
        if (entry == null || entry.id == null || entry.sha1 == null) {
            return null;
        }
        boolean direct = entry.versionId != null && entry.versionId.startsWith(DIRECT_VERSION_PREFIX);
        if (!direct && !running) {
            return null;
        }
        File archive = new File(cacheDir, entry.id + "-" + DatapackSupport.safeFile(entry.versionId) + ".zip");
        return matches(archive, entry.sha1) ? archive.toPath() : null;
    }

    private static Path download(File cacheDir, String source, Entry entry, DataVersion target) throws IOException {
        ResolvedDatapack resolved = ModrinthResolver.resolve(source, target.getVersion());
        String id = entry == null || entry.id == null ? DatapackArchive.deriveId(resolved) : entry.id;
        File archive = new File(cacheDir, id + "-" + DatapackSupport.safeFile(resolved.getVersionId()) + ".zip");
        String expected = resolved.getSha1();
        if (expected != null && !expected.isBlank() && matches(archive, expected)) {
            return archive.toPath();
        }
        DatapackSupport.ensureScratchDirectory(cacheDir, "datapack download cache");
        DatapackArchive.download(resolved.getDownloadUrl(), archive, null, null);
        if (expected != null && !expected.isBlank()) {
            String checksum = DatapackArchive.sha1(archive);
            if (!expected.equalsIgnoreCase(checksum)) {
                IO.delete(archive);
                throw new IOException("Checksum mismatch for " + id + " (expected " + expected + ", got " + checksum + ")");
            }
        }
        return archive.toPath();
    }

    private static boolean matches(File archive, String sha1) throws IOException {
        Path path = archive.toPath();
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        return sha1.equalsIgnoreCase(DatapackArchive.sha1(archive));
    }
}
