package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.HydrologyTile;
import art.arcane.iris.generation.hydrology.HydrologyTileCache;
import art.arcane.iris.generation.hydrology.HydrologyTileKey;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.runtime.Engine;
import com.sun.management.OperatingSystemMXBean;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Plans a rectangle of hydrology tiles of a real pack and prints one digest per tile (the prepared-plan
 * encoding, owner resolution included) with the planning CPU. Two builds plan identically when every
 * digest matches; an expected-digest file makes the probe fail on the first difference. With a pregen
 * radius the tiles are planned the way a pregeneration of that radius plans them: prepared-plan store,
 * pregeneration scope and speculative roots.
 */
public final class HydrologyPlanDigestProbe {
    private static final String LOG_PREFIX = "[hydroplan]";

    private HydrologyPlanDigestProbe() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 7) {
            throw new IllegalArgumentException("Expected: <pack> <dimension> <seed> <minimumTileX> <maximumTileX> "
                    + "<minimumTileZ> <maximumTileZ> [digestOut|-] [expectedDigests|-] [pregenRadius]");
        }
        File pack = new File(arguments[0]);
        String dimension = arguments[1];
        long seed = Long.parseLong(arguments[2]);
        int minimumTileX = Integer.parseInt(arguments[3]);
        int maximumTileX = Integer.parseInt(arguments[4]);
        int minimumTileZ = Integer.parseInt(arguments[5]);
        int maximumTileZ = Integer.parseInt(arguments[6]);
        Path output = arguments.length > 7 && !arguments[7].equals("-") ? Path.of(arguments[7]) : null;
        List<String> expected = arguments.length > 8 && !arguments[8].equals("-") ? Files.readAllLines(Path.of(arguments[8])) : null;
        Integer pregenRadius = arguments.length > 9 ? Integer.valueOf(arguments[9]) : null;
        List<HydrologyTileKey> keys = nearestFirst(minimumTileX, maximumTileX, minimumTileZ, maximumTileZ);
        OperatingSystemMXBean system = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        ArrayList<String> lines = new ArrayList<>(keys.size());
        int mismatches;
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(pack, dimension, LOG_PREFIX)) {
            try (RealPackProbeSupport.EngineSession session = workspace.openEngine(seed, false, "plan-" + seed)) {
                Engine engine = session.engine();
                IrisHydrologyRuntime runtime = engine.getComplex().getHydrologyRuntime();
                if (runtime == null) {
                    throw new IllegalStateException("Dimension '" + dimension + "' has no active hydrology runtime.");
                }
                Path store = pregenRadius == null ? null : Files.createTempDirectory("iris-hydroplan-store-");
                try {
                    if (store != null) {
                        runtime.enableSharedCache("probe", store);
                    }
                    mismatches = plan(runtime, keys, pregenRadius, expected, lines, system);
                } finally {
                    if (store != null) {
                        delete(store);
                    }
                }
            }
        }
        if (output != null) {
            Files.write(output, lines, StandardCharsets.UTF_8);
        }
        System.exit(mismatches == 0 ? 0 : 1);
    }

    private static int plan(IrisHydrologyRuntime runtime, List<HydrologyTileKey> keys, Integer pregenRadius,
                            List<String> expected, List<String> lines,
                            OperatingSystemMXBean system) throws Exception {
        int mismatches = 0;
        long cpuStart = system.getProcessCpuTime();
        long wallStart = System.nanoTime();
        try (HydrologyTileCache.PregenerationScope ignored = pregenRadius == null ? null
                : runtime.preparePregeneration(pregenerationArea(pregenRadius))) {
            for (HydrologyTileKey key : keys) {
                long tileCpu = system.getProcessCpuTime();
                long tileWall = System.nanoTime();
                HydrologyTile tile = runtime.tile(key);
                String line = key.tileX() + "," + key.tileZ() + " " + digest(tile);
                System.out.printf(Locale.ROOT, "%s tile %s courses=%d columns=%d wall_ms=%d cpu_ms=%d%n", LOG_PREFIX,
                        line, tile.courses().size(), tile.footprint().size(),
                        (System.nanoTime() - tileWall) / 1_000_000L, (system.getProcessCpuTime() - tileCpu) / 1_000_000L);
                if (expected != null && !expected.contains(line)) {
                    mismatches++;
                    System.out.println(LOG_PREFIX + " MISMATCH " + line);
                }
                lines.add(line);
            }
        }
        System.out.printf(Locale.ROOT, "%s RESULT tiles=%d wall_ms=%d cpu_ms=%d mismatches=%d%n", LOG_PREFIX,
                keys.size(), (System.nanoTime() - wallStart) / 1_000_000L,
                (system.getProcessCpuTime() - cpuStart) / 1_000_000L, mismatches);
        return mismatches;
    }

    private static void delete(Path root) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** The region-aligned area a square pregeneration of the radius covers, as the pregenerator reports it. */
    static HydrologyTileCache.PregenerationArea pregenerationArea(int radius) {
        long minimum = Math.floorDiv(-radius, 512) * 512L;
        long maximum = (Math.floorDiv(radius, 512) + 1L) * 512L - 1L;
        return new HydrologyTileCache.PregenerationArea(0, 0, minimum, minimum, maximum, maximum);
    }

    private static List<HydrologyTileKey> nearestFirst(int minimumTileX, int maximumTileX, int minimumTileZ, int maximumTileZ) {
        ArrayList<HydrologyTileKey> keys = new ArrayList<>();
        for (int tileZ = minimumTileZ; tileZ <= maximumTileZ; tileZ++) {
            for (int tileX = minimumTileX; tileX <= maximumTileX; tileX++) {
                keys.add(new HydrologyTileKey(tileX, tileZ));
            }
        }
        keys.sort(Comparator.comparingLong((HydrologyTileKey key) -> distanceSquared(key))
                .thenComparingInt(HydrologyTileKey::tileZ).thenComparingInt(HydrologyTileKey::tileX));
        return keys;
    }

    private static long distanceSquared(HydrologyTileKey key) {
        long x = key.tileX() * 2L + 1L;
        long z = key.tileZ() * 2L + 1L;
        return x * x + z * z;
    }

    private static String digest(HydrologyTile tile) throws Exception {
        Class<?> codecType = Class.forName("art.arcane.iris.generation.hydrology.HydrologyTileCodec");
        Constructor<?> constructor = codecType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Method write = codecType.getDeclaredMethod("write", DataOutputStream.class, HydrologyTile.class, String.class);
        write.setAccessible(true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            write.invoke(constructor.newInstance(), output, tile, "probe");
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
    }
}
