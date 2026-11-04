package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UncutTerrainDigest {
    private static final Pattern WINDOWS = Pattern.compile("(-?\\d+)\\.\\.(-?\\d+),(-?\\d+)\\.\\.(-?\\d+)");

    private UncutTerrainDigest() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 5) {
            throw new IllegalArgumentException("Usage: <pack> <dimension> <seed> <chunkX0..chunkX1,chunkZ0..chunkZ1> <output>");
        }
        Matcher windows = WINDOWS.matcher(arguments[3]);
        if (!windows.matches()) {
            throw new IllegalArgumentException("Bad chunk range " + arguments[3]);
        }
        File output = new File(arguments[4]);
        long columns = 0;
        long cuts = 0;
        StubPlatform.vanillaSolidity(true);
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(new File(arguments[0]), arguments[1], "[uncut-digest]");
             RealPackProbeSupport.EngineSession session = workspace.openEngine(Long.parseLong(arguments[2]), false, "uncut-digest");
             PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output.toPath(), StandardCharsets.UTF_8));
             PrintWriter cutWriter = new PrintWriter(Files.newBufferedWriter(new File(arguments[4] + ".cuts").toPath(), StandardCharsets.UTF_8))) {
            Engine engine = session.engine();
            MessageDigest total = MessageDigest.getInstance("SHA-256");
            for (int chunkX = Integer.parseInt(windows.group(1)); chunkX <= Integer.parseInt(windows.group(2)); chunkX++) {
                for (int chunkZ = Integer.parseInt(windows.group(3)); chunkZ <= Integer.parseInt(windows.group(4)); chunkZ++) {
                    ChunkDigest digest = chunk(engine, chunkX, chunkZ, cutWriter);
                    columns += digest.columns();
                    cuts += digest.cuts();
                    writer.println(chunkX + " " + chunkZ + " " + digest.sha());
                    total.update(digest.sha().getBytes(StandardCharsets.UTF_8));
                }
            }
            System.out.println("IRIS_UNCUT_DIGEST seed=" + arguments[2] + " columns=" + columns + " cuts=" + cuts
                    + " sha=" + HexFormat.of().formatHex(total.digest()));
        }
        System.exit(0);
    }

    private static ChunkDigest chunk(Engine engine, int chunkX, int chunkZ, PrintWriter cutWriter) throws NoSuchAlgorithmException {
        RealPackProbeSupport.GeneratedChunk chunk = RealPackProbeSupport.generateChunk(engine, chunkX, chunkZ);
        IrisComplex complex = engine.getComplex();
        ChunkWaterRaster raster = complex.getHydrologyRuntime().raster(chunkX, chunkZ);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        int columns = 0;
        int cuts = 0;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = (chunkX << 4) + lx;
                int z = (chunkZ << 4) + lz;
                WaterRole role = raster.role(lx, lz);
                boolean seaFloor = !role.wet() || role == WaterRole.INLET || role == WaterRole.MOUTH;
                if (role != WaterRole.NONE && seaFloor
                        && complex.naturalTrueHeight(x, z) > complex.getRoundedHeighteightStream().get(x, z)) {
                    cuts++;
                    cutWriter.println(x + " " + z);
                    continue;
                }
                columns++;
                for (int y = 0; y < chunk.height(); y++) {
                    NativeBlockState state = chunk.blocks().get(lx, y, lz);
                    digest.update((state == null ? "" : state.key()).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                }
            }
        }
        return new ChunkDigest(HexFormat.of().formatHex(digest.digest()), columns, cuts);
    }

    private record ChunkDigest(String sha, int columns, int cuts) {
    }
}
