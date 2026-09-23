package art.arcane.iris.probe;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngineMantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.mantle.runtime.Mantle;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class HeadlessMantleInspection {
    private HeadlessMantleInspection() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 6) {
            throw new IllegalArgumentException("Expected pack, dimension, seed, world, regionX, regionZ");
        }
        Path temporary = Files.createTempDirectory("iris-mantle-inspection-");
        Path copied = temporary.resolve("mantle");
        try {
            Process copy = new ProcessBuilder("cp", "-Rc", Path.of(arguments[3]).resolve("mantle-hydrology").toString(),
                    copied.toString()).inheritIO().start();
            if (copy.waitFor() != 0) {
                throw new IllegalStateException("Could not clone retained mantle for read-only inspection");
            }
            inspect(arguments, copied);
        } finally {
            try (Stream<Path> paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void inspect(String[] arguments, Path copied) throws Exception {
        HeadlessNativeRuntime runtime = new HeadlessNativeRuntime();
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(new RealPackProbeSupport.WorkspaceOptions(
                new File(arguments[0]), arguments[1], "[mantle-inspection]", runtime,
                Path.of(System.getProperty("java.io.tmpdir"))));
             RealPackProbeSupport.EngineSession session = workspace.openEngine(Long.parseLong(arguments[2]), false, "inspect")) {
            Engine engine = session.engine();
            Mantle<Matter> mantle = IrisEngineMantle.createMantle(engine, copied, engine::getData);
            try {
                AtomicInteger tiles = new AtomicInteger();
                Map<String, Integer> materials = new TreeMap<>();
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                int minimumX = Integer.parseInt(arguments[4]) << 5;
                int minimumZ = Integer.parseInt(arguments[5]) << 5;
                for (int z = minimumZ; z < minimumZ + 32; z++) {
                    for (int x = minimumX; x < minimumX + 32; x++) {
                        mantle.iterateChunk(x, z, TileWrapper.class, (localX, y, localZ, wrapper) -> {
                            TileData tile = wrapper.getData();
                            tiles.incrementAndGet();
                            materials.merge(tile.getMaterialKey(), 1, Integer::sum);
                            digest.update(encoded(tile));
                        });
                    }
                }
                System.out.println("retainedMantleTiles=" + tiles.get() + " materials=" + materials
                        + " sha256=" + HexFormat.of().formatHex(digest.digest()));
            } finally {
                mantle.close();
            }
        }
    }

    private static byte[] encoded(TileData tile) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            tile.toBinary(output);
            return bytes.toByteArray();
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot serialize retained tile " + tile.getMaterialKey(), failure);
        }
    }
}
