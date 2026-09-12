/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.validation;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeProgressMessages;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.ChunkSpiral;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.mantle.runtime.Mantle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public final class GoldenHashEngine {
    public static final String FALLBACK_BIOME_KEY = "minecraft:plains";

    public enum Mode {
        AUTO,
        CAPTURE,
        VERIFY
    }

    public interface ChunkSnapshot {
        int minY();

        int maxY();

        PlatformBlockState block(int x, int y, int z);

        PlatformBiome biome(int x, int y, int z);
    }

    public interface ChunkSource {
        ChunkSnapshot generate(int chunkX, int chunkZ) throws Exception;
    }

    public interface Feedback {
        void ok(String message);

        void warn(String message);

        void fail(String message);
    }

    public interface Progress {
        default void stage(String stage) {
        }

        default void total(int total) {
        }

        default void chunkDone(int chunkX, int chunkZ, boolean ok, int done, int total) {
        }

        default void chunkFailed(int chunkX, int chunkZ, Throwable error) {
        }
    }

    public record Request(String worldName, long seed, String mcVersion, int minY, int maxY, int centerChunkX,
                          int centerChunkZ, int radius, int threads, Mode mode, boolean resetMantle, boolean deep,
                          String nullBiomeKey) {
    }

    private static final AtomicInteger ACTIVE_SCANS = new AtomicInteger(0);
    private static final String FORMAT = "iris-goldenhash v1";
    private static final int BIOME_STEP = 4;
    private static final int MAX_REPORTED_MISMATCHES = 10;

    private final Engine engine;
    private final Request request;
    private final ChunkSource source;
    private final Feedback feedback;
    private final Progress progress;
    private final int radius;
    private final int threads;
    private final File goldenFile;

    public GoldenHashEngine(Engine engine, Request request, File goldenDir, ChunkSource source, Feedback feedback, Progress progress) {
        this.engine = engine;
        this.request = request;
        this.source = source;
        this.feedback = feedback;
        this.progress = progress;
        this.radius = Math.max(0, request.radius());
        this.threads = Math.max(1, request.threads());
        goldenDir.mkdirs();
        this.goldenFile = new File(goldenDir, engine.getDimension().getLoadKey()
                + "-s" + request.seed()
                + "-c" + request.centerChunkX() + "x" + request.centerChunkZ()
                + "-r" + this.radius + ".hashes");
    }

    public static boolean isActive() {
        return ACTIVE_SCANS.get() > 0;
    }

    public File getGoldenFile() {
        return goldenFile;
    }

    public boolean run() {
        ACTIVE_SCANS.incrementAndGet();
        try {
            boolean exists = goldenFile.exists();
            if (request.mode() == Mode.VERIFY && !exists) {
                feedback.fail(IrisLanguage.plain(
                        RuntimeProgressMessages.GOLDEN_NO_CAPTURE,
                        MessageArgument.untrusted("path", goldenFile.getAbsolutePath())
                ));
                return false;
            }

            if (request.resetMantle()) {
                progress.stage(IrisLanguage.plain(RuntimeProgressMessages.CHUNK_STAGE_RESETTING_MANTLE));
                if (!resetMantleFull()) {
                    // A partial reset silently invalidates the capture; never scan over it.
                    return false;
                }
            }

            List<int[]> targets = ChunkSpiral.centerOut(request.centerChunkX(), request.centerChunkZ(), radius);
            progress.total(targets.size());
            progress.stage(IrisLanguage.plain(RuntimeProgressMessages.CHUNK_STAGE_GENERATING));
            Map<Long, String> lines = scan(targets);

            if (lines.size() != targets.size()) {
                feedback.fail(IrisLanguage.plain(
                        RuntimeProgressMessages.GOLDEN_ABORTED,
                        MessageArgument.trusted("failed", targets.size() - lines.size())
                ));
                return false;
            }

            progress.stage(IrisLanguage.plain(RuntimeProgressMessages.CHUNK_STAGE_COMPARING));
            if (request.mode() == Mode.CAPTURE || (request.mode() == Mode.AUTO && !exists)) {
                capture(lines);
                return true;
            }

            return verify(lines);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            feedback.fail(IrisLanguage.plain(
                    RuntimeProgressMessages.GOLDEN_FAILED,
                    MessageArgument.untrusted("error", String.valueOf(e))
            ));
            return false;
        } finally {
            ACTIVE_SCANS.decrementAndGet();
        }
    }

    private boolean resetMantleFull() {
        Mantle mantle = engine.getMantle().getMantle();
        try {
            // resetStorage invalidates the IOWorker channel cache before unlinking; deleting
            // behind cached channels sent every post-reset plate write to an unlinked inode.
            mantle.resetStorage();
            feedback.ok(IrisLanguage.plain(
                    RuntimeProgressMessages.GOLDEN_MANTLE_RESET,
                    MessageArgument.untrusted("path", mantle.getDataFolder().getAbsolutePath())
            ));
            return true;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            feedback.warn(IrisLanguage.plain(
                    RuntimeProgressMessages.GOLDEN_MANTLE_RESET_FAILED,
                    MessageArgument.untrusted("type", e.getClass().getSimpleName())
            ));
            return false;
        }
    }

    private Map<Long, String> scan(List<int[]> targets) throws InterruptedException {
        Map<Long, String> lines = new ConcurrentHashMap<>();
        Semaphore inFlight = new Semaphore(threads);
        CountDownLatch done = new CountDownLatch(targets.size());
        AtomicInteger completed = new AtomicInteger();
        int total = targets.size();

        for (int[] target : targets) {
            int chunkX = target[0];
            int chunkZ = target[1];

            inFlight.acquire();
            MultiBurst.burst.lazy(() -> {
                boolean ok = false;
                try {
                    ChunkSnapshot snapshot = source.generate(chunkX, chunkZ);
                    lines.put(chunkKey(chunkX, chunkZ), hashChunk(chunkX, chunkZ, snapshot));
                    if (request.deep()) {
                        writeDeepDump(chunkX, chunkZ, snapshot);
                    }
                    ok = true;
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                    progress.chunkFailed(chunkX, chunkZ, e);
                } finally {
                    progress.chunkDone(chunkX, chunkZ, ok, completed.incrementAndGet(), total);
                    inFlight.release();
                    done.countDown();
                }
            });
        }

        done.await();
        return lines;
    }

    private String hashChunk(int chunkX, int chunkZ, ChunkSnapshot snapshot) {
        MessageDigest blockDigest = sha256();
        MessageDigest biomeDigest = sha256();
        int minY = snapshot.minY();
        int maxY = snapshot.maxY();
        IdentityHashMap<PlatformBlockState, byte[]> blockCache = new IdentityHashMap<>();
        Map<PlatformBiome, byte[]> biomeCache = new HashMap<>();
        byte[] nullBiome = (request.nullBiomeKey() + "\n").getBytes(StandardCharsets.UTF_8);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    PlatformBlockState data = snapshot.block(x, y, z);
                    byte[] bytes = blockCache.computeIfAbsent(data, (PlatformBlockState d) -> (d.key() + "\n").getBytes(StandardCharsets.UTF_8));
                    blockDigest.update(bytes);
                }
            }
        }

        for (int x = 0; x < 16; x += BIOME_STEP) {
            for (int z = 0; z < 16; z += BIOME_STEP) {
                for (int y = minY; y < maxY; y += BIOME_STEP) {
                    PlatformBiome biome = snapshot.biome(x, y, z);
                    byte[] bytes = biome == null
                            ? nullBiome
                            : biomeCache.computeIfAbsent(biome, (PlatformBiome b) -> (b.key() + "\n").getBytes(StandardCharsets.UTF_8));
                    biomeDigest.update(bytes);
                }
            }
        }

        return chunkX + " " + chunkZ + " "
                + HexFormat.of().formatHex(blockDigest.digest()) + " "
                + HexFormat.of().formatHex(biomeDigest.digest());
    }

    private void capture(Map<Long, String> lines) throws IOException {
        List<String> body = orderedBody(lines);
        String combined = combinedHash(body);
        List<String> out = new ArrayList<>();
        out.add("#" + FORMAT);
        out.add("#world=" + request.worldName());
        out.add("#dim=" + engine.getDimension().getLoadKey());
        out.add("#seed=" + request.seed());
        out.add("#mc=" + request.mcVersion());
        out.add("#minY=" + request.minY() + " maxY=" + request.maxY());
        out.add("#center=" + request.centerChunkX() + "," + request.centerChunkZ());
        out.add("#radius=" + radius);
        out.addAll(body);
        out.add("#combined=" + combined);
        Files.write(goldenFile.toPath(), out, StandardCharsets.UTF_8);

        feedback.ok(IrisLanguage.plain(
                RuntimeProgressMessages.GOLDEN_CAPTURED,
                MessageArgument.trusted("chunks", body.size()),
                MessageArgument.trusted("hash", shortHash(combined))
        ));
        feedback.ok(goldenFile.getAbsolutePath());
        IrisLogging.info("goldenhash captured: " + goldenFile.getAbsolutePath() + " combined=" + combined);
    }

    private boolean verify(Map<Long, String> lines) throws IOException {
        List<String> existing = Files.readAllLines(goldenFile.toPath(), StandardCharsets.UTF_8);
        Map<String, String> meta = new HashMap<>();
        Map<String, String> goldenChunks = new HashMap<>();
        for (String line : existing) {
            if (line.startsWith("#")) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    meta.put(line.substring(1, eq), line.substring(eq + 1));
                }
            } else if (!line.isBlank()) {
                int second = line.indexOf(' ', line.indexOf(' ') + 1);
                goldenChunks.put(line.substring(0, second), line);
            }
        }

        String expectedSeed = String.valueOf(request.seed());
        String expectedDim = engine.getDimension().getLoadKey();
        if (!expectedSeed.equals(meta.get("seed")) || !expectedDim.equals(meta.get("dim"))) {
            feedback.fail(IrisLanguage.plain(
                    RuntimeProgressMessages.GOLDEN_WRONG_WORLD,
                    MessageArgument.untrusted("goldenDimension", String.valueOf(meta.get("dim"))),
                    MessageArgument.untrusted("goldenSeed", String.valueOf(meta.get("seed"))),
                    MessageArgument.untrusted("dimension", expectedDim),
                    MessageArgument.untrusted("seed", expectedSeed)
            ));
            return false;
        }
        if (!request.mcVersion().equals(meta.get("mc"))) {
            feedback.warn(IrisLanguage.plain(
                    RuntimeProgressMessages.GOLDEN_VERSION_WARNING,
                    MessageArgument.untrusted("goldenVersion", String.valueOf(meta.get("mc"))),
                    MessageArgument.untrusted("version", request.mcVersion())
            ));
        }

        List<String> body = orderedBody(lines);
        List<String> mismatches = new ArrayList<>();
        // Display strings and diagnosis input are different shapes: diagnose() parses a bare
        // "<x> <z>" key, and feeding it a localized sentence aborted the diagnosis whenever
        // the FIRST mismatch was a chunk missing from the golden capture.
        String firstMismatchKey = null;
        for (String line : body) {
            int second = line.indexOf(' ', line.indexOf(' ') + 1);
            String key = line.substring(0, second);
            String golden = goldenChunks.get(key);
            if (!line.equals(golden)) {
                if (firstMismatchKey == null) {
                    firstMismatchKey = key;
                }
                mismatches.add(golden == null
                        ? IrisLanguage.plain(
                                RuntimeProgressMessages.GOLDEN_MISSING_IN_GOLDEN,
                                MessageArgument.untrusted("chunk", key)
                        )
                        : key);
            }
        }

        String combined = combinedHash(body);
        if (mismatches.isEmpty()) {
            feedback.ok(IrisLanguage.plain(
                    RuntimeProgressMessages.GOLDEN_MATCH,
                    MessageArgument.trusted("current", body.size()),
                    MessageArgument.trusted("golden", goldenChunks.size()),
                    MessageArgument.trusted("hash", shortHash(combined))
            ));
            IrisLogging.info("goldenhash MATCH: " + goldenFile.getName() + " combined=" + combined);
            return true;
        }

        feedback.fail(IrisLanguage.plain(
                RuntimeProgressMessages.GOLDEN_MISMATCH,
                MessageArgument.trusted("mismatches", mismatches.size()),
                MessageArgument.trusted("chunks", body.size())
        ));
        for (int i = 0; i < Math.min(MAX_REPORTED_MISMATCHES, mismatches.size()); i++) {
            feedback.fail(IrisLanguage.plain(
                    RuntimeProgressMessages.GOLDEN_MISMATCH_CHUNK,
                    MessageArgument.untrusted("chunk", mismatches.get(i))
            ));
        }
        if (mismatches.size() > MAX_REPORTED_MISMATCHES) {
            feedback.fail(IrisLanguage.plain(
                    RuntimeProgressMessages.GOLDEN_MISMATCH_MORE,
                    MessageArgument.trusted("count", mismatches.size() - MAX_REPORTED_MISMATCHES)
            ));
        }

        File current = new File(goldenFile.getParentFile(), goldenFile.getName() + ".new");
        List<String> out = new ArrayList<>(body);
        out.add("#combined=" + combined);
        Files.write(current.toPath(), out, StandardCharsets.UTF_8);
        feedback.warn(IrisLanguage.plain(
                RuntimeProgressMessages.GOLDEN_CURRENT_WRITTEN,
                MessageArgument.untrusted("file", current.getName())
        ));
        IrisLogging.info("goldenhash MISMATCH: " + mismatches.size() + "/" + body.size() + " -> " + current.getAbsolutePath());

        progress.stage(IrisLanguage.plain(RuntimeProgressMessages.CHUNK_STAGE_DIAGNOSING));
        diagnose(firstMismatchKey);
        return false;
    }

    private void diagnose(String mismatchKey) {
        try {
            String[] parts = mismatchKey.trim().split(" ");
            int chunkX = Integer.parseInt(parts[0]);
            int chunkZ = Integer.parseInt(parts[1]);

            ChunkSnapshot first = source.generate(chunkX, chunkZ);
            ChunkSnapshot second = source.generate(chunkX, chunkZ);

            int minY = first.minY();
            int maxY = first.maxY();
            List<String> diffs = new ArrayList<>();
            for (int x = 0; x < 16 && diffs.size() < 50; x++) {
                for (int z = 0; z < 16 && diffs.size() < 50; z++) {
                    for (int y = minY; y < maxY && diffs.size() < 50; y++) {
                        String a = first.block(x, y, z).key();
                        String b = second.block(x, y, z).key();
                        if (!a.equals(b)) {
                            diffs.add(x + " " + y + " " + z + " | " + a + " | " + b);
                        }
                    }
                }
            }

            List<String> mantleDiffs = new ArrayList<>();
            String mantleStatus;
            boolean mantleStable = false;
            try {
                EngineMantle engineMantle = engine.getMantle();
                int margin = Math.max(engineMantle.getRadius(), engineMantle.getRealRadius()) + 1;
                for (int dx = -margin; dx <= margin; dx++) {
                    for (int dz = -margin; dz <= margin; dz++) {
                        engineMantle.getMantle().deleteChunk(chunkX + dx, chunkZ + dz);
                    }
                }
                ChunkSnapshot reset = source.generate(chunkX, chunkZ);
                for (int x = 0; x < 16 && mantleDiffs.size() < 80; x++) {
                    for (int z = 0; z < 16 && mantleDiffs.size() < 80; z++) {
                        for (int y = minY; y < maxY && mantleDiffs.size() < 80; y++) {
                            String a = first.block(x, y, z).key();
                            String b = reset.block(x, y, z).key();
                            if (!a.equals(b)) {
                                mantleDiffs.add(x + " " + y + " " + z + " | scan: " + a + " | mantle-reset: " + b);
                            }
                        }
                    }
                }
                mantleStable = mantleDiffs.isEmpty();
                mantleStatus = mantleStable
                        ? IrisLanguage.plain(RuntimeProgressMessages.GOLDEN_MANTLE_STABLE)
                        : IrisLanguage.plain(
                                RuntimeProgressMessages.GOLDEN_MANTLE_DIVERGED,
                                MessageArgument.trusted("diffs", mantleDiffs.size())
                        );
            } catch (Throwable t) {
                mantleDiffs.clear();
                mantleStatus = IrisLanguage.plain(
                        RuntimeProgressMessages.GOLDEN_MANTLE_SKIPPED,
                        MessageArgument.untrusted("type", t.getClass().getSimpleName())
                );
            }

            List<String> report = new ArrayList<>();
            report.add("#goldenhash diagnosis chunk=" + chunkX + "," + chunkZ);
            report.add("#repeat-generation: " + (diffs.isEmpty() ? "STABLE (nondeterminism is order/state-dependent, not per-call)" : "UNSTABLE (" + diffs.size() + "+ diffs between two back-to-back generations)"));
            report.addAll(diffs);
            report.add("#mantle-reset regeneration: " + mantleStatus);
            report.addAll(mantleDiffs);
            report.add("#full non-air dump of generation 1 follows (x y z state)");
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        String state = first.block(x, y, z).key();
                        if (!state.equals("minecraft:air") && !state.equals("minecraft:cave_air") && !state.equals("minecraft:void_air")) {
                            report.add(x + " " + y + " " + z + " " + state);
                        }
                    }
                }
            }

            File diag = new File(goldenFile.getParentFile(), goldenFile.getName() + ".diag-c" + chunkX + "x" + chunkZ + ".txt");
            Files.write(diag.toPath(), report, StandardCharsets.UTF_8);
            if (diffs.isEmpty() && mantleStable) {
                feedback.warn(IrisLanguage.plain(
                        RuntimeProgressMessages.GOLDEN_DIAG_STABLE,
                        MessageArgument.trusted("mantleStatus", mantleStatus),
                        MessageArgument.untrusted("file", diag.getName())
                ));
            } else {
                feedback.fail(IrisLanguage.plain(
                        RuntimeProgressMessages.GOLDEN_DIAG_UNSTABLE,
                        MessageArgument.trusted("diffs", diffs.size()),
                        MessageArgument.trusted("mantleStatus", mantleStatus),
                        MessageArgument.untrusted("file", diag.getName())
                ));
            }
            IrisLogging.debug("goldenhash diag: chunk=" + chunkX + "," + chunkZ + " repeatStable=" + diffs.isEmpty() + " -> " + diag.getAbsolutePath());
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            feedback.fail(IrisLanguage.plain(
                    RuntimeProgressMessages.GOLDEN_DIAG_FAILED,
                    MessageArgument.untrusted("error", String.valueOf(e.getMessage()))
            ));
        }
    }

    private void writeDeepDump(int chunkX, int chunkZ, ChunkSnapshot snapshot) throws IOException {
        File dir = new File(goldenFile.getParentFile(), goldenFile.getName() + (goldenFile.exists() ? ".deep-verify" : ".deep"));
        dir.mkdirs();
        int minY = snapshot.minY();
        int maxY = snapshot.maxY();
        List<String> out = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    String state = snapshot.block(x, y, z).key();
                    if (!state.equals("minecraft:air") && !state.equals("minecraft:cave_air") && !state.equals("minecraft:void_air")) {
                        out.add(x + " " + y + " " + z + " " + state);
                    }
                }
            }
        }
        Files.write(new File(dir, chunkX + "_" + chunkZ + ".txt").toPath(), out, StandardCharsets.UTF_8);
    }

    private List<String> orderedBody(Map<Long, String> lines) {
        Map<Long, String> sorted = new TreeMap<>(lines);
        return new ArrayList<>(sorted.values());
    }

    private String combinedHash(List<String> body) {
        MessageDigest digest = sha256();
        for (String line : body) {
            digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String shortHash(String hex) {
        return hex.substring(0, 12);
    }

    private static long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
