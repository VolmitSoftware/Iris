package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stream.ProvisionalAnswerException;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.stream.ProceduralStream;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public final class StructureWaterFootprint {
    private static final int MAX_WATER_PERCENT = 10;
    private static final int CHUNK = 16;

    private StructureWaterFootprint() {
    }

    public static boolean admitsChunk(Engine engine, IrisStructurePlacement placement, String structureKey,
                                      int chunkX, int chunkZ) {
        IrisComplex complex = engine.getComplex();
        if (placement.isAllowWater() || complex == null) {
            return true;
        }
        ProceduralStream<WaterRole> roles = complex.getWaterRoleStream();
        long revision = ProvisionalSampling.revision();
        int water = 0;
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (int x = minX; x < minX + CHUNK; x++) {
            for (int z = minZ; z < minZ + CHUNK; z++) {
                if (coversWater(roles.get(x, z))) {
                    water++;
                }
            }
        }
        requireFinal(complex, revision, structureKey, chunkX, chunkZ);
        return admits(water, CHUNK * CHUNK);
    }

    public static boolean admitsPieces(Engine engine, IrisStructurePlacement placement, String structureKey,
                                       KList<PlacedStructurePiece> pieces, int chunkX, int chunkZ) {
        IrisComplex complex = engine.getComplex();
        if (placement.isAllowWater() || complex == null) {
            return true;
        }
        LongOpenHashSet columns = new LongOpenHashSet();
        for (PlacedStructurePiece piece : pieces) {
            for (int x = piece.getMinX(); x <= piece.getMaxX(); x++) {
                for (int z = piece.getMinZ(); z <= piece.getMaxZ(); z++) {
                    columns.add(columnKey(x, z));
                }
            }
        }
        ProceduralStream<WaterRole> roles = complex.getWaterRoleStream();
        long revision = ProvisionalSampling.revision();
        int water = 0;
        LongIterator iterator = columns.iterator();
        while (iterator.hasNext()) {
            long column = iterator.nextLong();
            if (coversWater(roles.get((int) (column >> 32), (int) column))) {
                water++;
            }
        }
        requireFinal(complex, revision, structureKey, chunkX, chunkZ);
        return admits(water, columns.size());
    }

    static boolean coversWater(WaterRole role) {
        return switch (role) {
            case CHANNEL, POND, CASCADE, WATERFALL -> true;
            case NONE, BANK, SHORE, POND_SHORE, DAM, MOUTH, INLET -> false;
        };
    }

    private static boolean admits(int water, int columns) {
        return (long) water * 100L <= (long) columns * MAX_WATER_PERCENT;
    }

    private static void requireFinal(IrisComplex complex, long revision, String structureKey, int chunkX, int chunkZ) {
        if (ProvisionalSampling.revision() == revision || complex.isPlatformMainThread()) {
            return;
        }
        throw new ProvisionalAnswerException("Structure '" + structureKey + "' starting in chunk " + chunkX + "," + chunkZ
                + " read a provisional water answer; structures are only placed from final water.");
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}
