/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.modded;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ModdedParityProbe {
    private static final String DIMENSION_KEY = "overworld";
    private static final long SEED = 1337L;
    private static final int BIOME_STEP = 4;
    private static final String DEFAULT_EXPECTED = "922bbcbe766d";
    private static final List<Throwable> REPORTED = Collections.synchronizedList(new ArrayList<>());

    private ModdedParityProbe() {
    }

    public static void schedule(String config) {
        Thread thread = new Thread(() -> waitAndRun(config), "Iris Parity Probe");
        thread.setDaemon(true);
        thread.start();
    }

    private static void waitAndRun(String config) {
        long start = System.currentTimeMillis();
        MinecraftServer server = null;
        while (System.currentTimeMillis() - start < 600000L) {
            MinecraftServer candidate = ModdedEngineBootstrap.currentServer();
            if (candidate != null && candidate.isReady()) {
                server = candidate;
                break;
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        if (server == null) {
            ModdedIrisLog.error("[parity] server did not become ready within 10 minutes");
            return;
        }

        boolean match = false;
        try {
            match = run(server, config);
        } catch (Throwable e) {
            ModdedIrisLog.error("[parity] probe failed", e);
        }

        ModdedIrisLog.info("[parity] shutting down dev server (result={})", match ? "MATCH" : "MISMATCH");
        server.halt(false);
    }

    private static boolean run(MinecraftServer server, String config) throws Exception {
        String packPath = config;
        int radius = 8;
        int lastColon = config.lastIndexOf(':');
        if (lastColon > 0) {
            String tail = config.substring(lastColon + 1);
            try {
                radius = Integer.parseInt(tail);
                packPath = config.substring(0, lastColon);
            } catch (NumberFormatException ignored) {
            }
        }

        File packSource = new File(packPath);
        if (!packSource.isDirectory()) {
            ModdedIrisLog.error("[parity] pack folder not found: {}", packSource.getAbsolutePath());
            return false;
        }

        ModdedEngineBootstrap.bind();
        ModdedPlatform.errorSink(REPORTED::add);

        File workRoot = Files.createTempDirectory("iris-parity").toFile();
        File pack = clonePack(packSource, workRoot);
        ModdedIrisLog.info("[parity] pack: {}", packSource.getAbsolutePath());
        ModdedIrisLog.info("[parity] work copy: {}", pack.getAbsolutePath());
        ModdedIrisLog.info("[parity] radius: {} ({} chunks)", radius, (2 * radius + 1) * (2 * radius + 1));

        IrisData data = IrisData.get(pack);
        IrisDimension dimension = data.getDimensionLoader().load(DIMENSION_KEY);
        if (dimension == null) {
            ModdedIrisLog.error("[parity] dimension '{}' did not load from {}", DIMENSION_KEY, pack.getAbsolutePath());
            return false;
        }

        IrisWorld world = IrisWorld.builder()
                .platformIdentity("iris:parity")
                .name("parity")
                .seed(SEED)
                .worldFolder(new File(workRoot, "world"))
                .minHeight(dimension.getMinHeight())
                .maxHeight(dimension.getMaxHeight())
                .build();
        EngineTarget target = new EngineTarget(world, dimension, data);
        Engine engine = new IrisEngine(target, IrisEngine.InitializationMode.RUNTIME);

        settle();
        int minY = dimension.getMinHeight();
        int maxY = dimension.getMaxHeight();
        int height = maxY - minY;
        ModdedIrisLog.info("[parity] engine up: dim={} seed={} minY={} maxY={}", engine.getDimension().getLoadKey(), engine.getSeedManager().getSeed(), minY, maxY);

        Map<String, String> goldenChunks = new HashMap<>();
        String goldenCombined = null;
        int goldenRadius = -1;
        String goldenPath = System.getProperty("iris.parity.golden");
        if (goldenPath != null) {
            List<String> goldenLines = Files.readAllLines(Path.of(goldenPath), StandardCharsets.UTF_8);
            for (String line : goldenLines) {
                if (line.startsWith("#")) {
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String metaKey = line.substring(1, eq);
                        String metaValue = line.substring(eq + 1);
                        if (metaKey.equals("combined")) {
                            goldenCombined = metaValue;
                        }
                        if (metaKey.equals("radius")) {
                            goldenRadius = Integer.parseInt(metaValue.trim());
                        }
                    }
                } else if (!line.isBlank()) {
                    int second = line.indexOf(' ', line.indexOf(' ') + 1);
                    goldenChunks.put(line.substring(0, second), line);
                }
            }
            ModdedIrisLog.info("[parity] golden: {} ({} chunks, combined={})", goldenPath, goldenChunks.size(), goldenCombined);
        }

        PlatformBlockState airState = IrisPlatforms.get().registries().air();
        List<int[]> targets = orderedTargets(0, 0, radius);
        Map<Long, String> lines = new TreeMap<>();
        List<String> mismatches = new ArrayList<>();
        int failed = 0;

        for (int[] at : targets) {
            int cx = at[0];
            int cz = at[1];
            drainReported();
            ModdedBlockBuffer blocks = new ModdedBlockBuffer(height, airState);
            Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(16, height, 16);
            List<Throwable> failures = new ArrayList<>();
            try {
                engine.generate(cx << 4, cz << 4, blocks, biomes, false);
            } catch (Throwable e) {
                failures.add(e);
            }
            failures.addAll(drainReported());

            if (!failures.isEmpty()) {
                failed++;
                ModdedIrisLog.error("[parity] chunk {},{} FAILED ({} error(s))", cx, cz, failures.size());
                for (Throwable failure : failures) {
                    ModdedIrisLog.error("[parity] chunk {},{} error", cx, cz, failure);
                }
                continue;
            }

            String line = hashChunk(cx, cz, blocks, biomes, height);
            lines.put(chunkKey(cx, cz), line);

            String key = cx + " " + cz;
            String golden = goldenChunks.get(key);
            if (golden != null && !golden.equals(line)) {
                mismatches.add(key);
                ModdedIrisLog.warn("[parity] chunk {} MISMATCH", key);
                ModdedIrisLog.warn("[parity]   golden: {}", golden);
                ModdedIrisLog.warn("[parity]   actual: {}", line);
                if (mismatches.size() == 1) {
                    diffDeep(cx, cz, blocks, height, minY);
                }
            }
        }

        List<String> body = new ArrayList<>(lines.values());
        String combined = combinedHash(body);
        String expected = goldenCombined != null && (goldenRadius == radius || goldenRadius < 0)
                ? goldenCombined.substring(0, 12)
                : DEFAULT_EXPECTED;
        boolean combinedMatch = combined.startsWith(expected);
        boolean chunkMatch = mismatches.isEmpty() && failed == 0;
        boolean match = goldenChunks.isEmpty() ? combinedMatch : (chunkMatch && (radius != 8 || combinedMatch));

        if (!goldenChunks.isEmpty()) {
            ModdedIrisLog.info("[parity] per-chunk: {}/{} matched golden ({} failed)", body.size() - mismatches.size(), body.size(), failed);
        }
        ModdedIrisLog.info("[parity] combined={} expected={} {} ({}/{})",
                combined.substring(0, 12), expected, match ? "MATCH" : "MISMATCH", body.size() - mismatches.size(), targets.size());
        return match;
    }

    private static void diffDeep(int cx, int cz, ModdedBlockBuffer blocks, int height, int minY) {
        String deepDir = System.getProperty("iris.parity.deep");
        if (deepDir == null) {
            return;
        }
        try {
            Path goldenDump = Path.of(deepDir, cx + "_" + cz + ".txt");
            if (!Files.exists(goldenDump)) {
                ModdedIrisLog.warn("[parity] no deep dump for chunk {},{} at {}", cx, cz, goldenDump);
                return;
            }
            List<String> golden = Files.readAllLines(goldenDump, StandardCharsets.UTF_8);
            List<String> actual = new ArrayList<>();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < height; y++) {
                        String state = blocks.get(x, y, z).key();
                        if (!state.equals("minecraft:air") && !state.equals("minecraft:cave_air") && !state.equals("minecraft:void_air")) {
                            actual.add(x + " " + (y + minY) + " " + z + " " + state);
                        }
                    }
                }
            }
            int shown = 0;
            int max = Math.max(golden.size(), actual.size());
            for (int i = 0; i < max && shown < 20; i++) {
                String g = i < golden.size() ? golden.get(i) : "<missing>";
                String a = i < actual.size() ? actual.get(i) : "<missing>";
                if (!g.equals(a)) {
                    ModdedIrisLog.warn("[parity]   deep diff line {}: golden='{}' actual='{}'", i, g, a);
                    shown++;
                }
            }
            File out = new File(IrisPlatforms.get().dataFolder("parity"), "deep-" + cx + "_" + cz + ".txt");
            Files.write(out.toPath(), actual, StandardCharsets.UTF_8);
            ModdedIrisLog.warn("[parity]   full actual dump: {}", out.getAbsolutePath());
        } catch (Throwable e) {
            ModdedIrisLog.warn("[parity] deep diff failed", e);
        }
    }

    private static List<int[]> orderedTargets(int centerX, int centerZ, int radius) {
        List<int[]> targets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                targets.add(new int[]{centerX + dx, centerZ + dz});
            }
        }
        targets.sort(Comparator.comparingInt((int[] t) -> {
            int ox = t[0] - centerX;
            int oz = t[1] - centerZ;
            return ox * ox + oz * oz;
        }));
        return targets;
    }

    private static String hashChunk(int chunkX, int chunkZ, ModdedBlockBuffer blocks, Hunk<PlatformBiome> biomes, int height) {
        MessageDigest blockDigest = sha256();
        MessageDigest biomeDigest = sha256();
        Map<PlatformBlockState, byte[]> blockCache = new HashMap<>();
        Map<PlatformBiome, byte[]> biomeCache = new HashMap<>();
        byte[] plains = "minecraft:plains\n".getBytes(StandardCharsets.UTF_8);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < height; y++) {
                    PlatformBlockState state = blocks.get(x, y, z);
                    byte[] bytes = blockCache.computeIfAbsent(state, (PlatformBlockState s) -> (s.key() + "\n").getBytes(StandardCharsets.UTF_8));
                    blockDigest.update(bytes);
                }
            }
        }

        for (int x = 0; x < 16; x += BIOME_STEP) {
            for (int z = 0; z < 16; z += BIOME_STEP) {
                for (int y = 0; y < height; y += BIOME_STEP) {
                    PlatformBiome biome = biomes.get(x, y, z);
                    byte[] bytes = biome == null
                            ? plains
                            : biomeCache.computeIfAbsent(biome, (PlatformBiome b) -> (b.key() + "\n").getBytes(StandardCharsets.UTF_8));
                    biomeDigest.update(bytes);
                }
            }
        }

        return chunkX + " " + chunkZ + " "
                + HexFormat.of().formatHex(blockDigest.digest()) + " "
                + HexFormat.of().formatHex(biomeDigest.digest());
    }

    private static String combinedHash(List<String> body) {
        MessageDigest digest = sha256();
        for (String line : body) {
            digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static File clonePack(File source, File workRoot) throws Exception {
        File destination = new File(workRoot, source.getName());
        Process clone = new ProcessBuilder("cp", "-Rc", source.getAbsolutePath(), destination.getAbsolutePath())
                .inheritIO()
                .start();
        if (clone.waitFor() != 0) {
            Process copy = new ProcessBuilder("cp", "-R", source.getAbsolutePath(), destination.getAbsolutePath())
                    .inheritIO()
                    .start();
            if (copy.waitFor() != 0) {
                throw new IllegalStateException("Failed to copy pack to " + destination.getAbsolutePath());
            }
        }
        return destination;
    }

    private static void settle() throws InterruptedException {
        long quietSince = System.currentTimeMillis();
        long start = quietSince;
        while (System.currentTimeMillis() - start < 15000L) {
            List<Throwable> batch = drainReported();
            if (batch.isEmpty()) {
                if (System.currentTimeMillis() - quietSince >= 1500L) {
                    break;
                }
            } else {
                for (Throwable error : batch) {
                    ModdedIrisLog.warn("[parity] engine-init reported error (non-fatal)", error);
                }
                quietSince = System.currentTimeMillis();
            }
            Thread.sleep(50L);
        }
    }

    private static List<Throwable> drainReported() {
        synchronized (REPORTED) {
            List<Throwable> drained = new ArrayList<>(REPORTED);
            REPORTED.clear();
            return drained;
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
