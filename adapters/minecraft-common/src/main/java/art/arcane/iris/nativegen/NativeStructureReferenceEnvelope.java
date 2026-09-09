package art.arcane.iris.nativegen;

import art.arcane.iris.engine.object.IrisStructureTerrain;
import art.arcane.iris.engine.object.IrisStructureTerrainMode;
import art.arcane.iris.spi.IrisLogging;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NativeStructureReferenceEnvelope {
    private static final int MAX_REFERENCE_DISTANCE_CHUNKS = 8;
    private static final Set<String> WARNED_CLIPPED_TERRAIN = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_SKIPPED_CONTENT = ConcurrentHashMap.newKeySet();

    private NativeStructureReferenceEnvelope() {
    }

    public static StructureStart wrap(StructureStart generated, Structure source, int references,
                                      IrisStructureTerrain terrain) {
        return wrap(generated, source, references, terrain, null);
    }

    public static StructureStart wrap(StructureStart generated, Structure source, int references,
                                      IrisStructureTerrain terrain, String structureKey) {
        referenceBounds(generated, source, terrain, structureKey);
        return new StructureStart(
                source,
                generated.getChunkPos(),
                references,
                new PiecesContainer(List.copyOf(generated.getPieces()))
        );
    }

    public static StructureStart wrapForPublication(
            StructureStart generated, Structure source, int references,
            IrisStructureTerrain terrain, String structureKey) {
        try {
            return wrap(generated, source, references, terrain, structureKey);
        } catch (UnrepresentableContentException exception) {
            warnSkippedContent(generated, source, structureKey);
            return StructureStart.INVALID_START;
        }
    }

    public static BoundingBox referenceBounds(StructureStart start, Structure source,
                                              IrisStructureTerrain terrain) {
        return referenceBounds(start, source, terrain, null);
    }

    public static BoundingBox referenceBounds(StructureStart start, Structure source,
                                              IrisStructureTerrain terrain,
                                              String structureKey) {
        IrisStructureTerrainMode mode = terrain == null
                ? IrisStructureTerrainMode.PRESERVE : terrain.resolvedMode();
        boolean usesEnvelope = mode == IrisStructureTerrainMode.BORE
                || mode == IrisStructureTerrainMode.FORCE_CARVE
                || mode == IrisStructureTerrainMode.VACUUM
                || mode == IrisStructureTerrainMode.FLATTEN
                || mode == IrisStructureTerrainMode.ENCASE;
        int horizontalPadding = mode == IrisStructureTerrainMode.VACUUM
                ? NativeStructureSurfaceFitter.surfaceTerrainRadius()
                : usesEnvelope ? Math.max(0, terrain.getHorizontalPadding()) : 0;
        BoundingBox content = contentBounds(start);
        if (!fitsReferenceRange(start.getChunkPos(), content)) {
            throw new UnrepresentableContentException("Native structure content at "
                    + start.getChunkPos().x() + "," + start.getChunkPos().z()
                    + " exceeds Minecraft's " + MAX_REFERENCE_DISTANCE_CHUNKS
                    + "-chunk structure reference range");
        }
        BoundingBox adjustedEnvelope;
        boolean arithmeticClipped = false;
        try {
            BoundingBox envelope = horizontalPadding == 0
                    ? content
                    : new BoundingBox(
                    Math.subtractExact(content.minX(), horizontalPadding),
                    content.minY(),
                    Math.subtractExact(content.minZ(), horizontalPadding),
                    Math.addExact(content.maxX(), horizontalPadding),
                    content.maxY(),
                    Math.addExact(content.maxZ(), horizontalPadding)
            );
            adjustedEnvelope = mode == IrisStructureTerrainMode.SOURCE
                    ? source.adjustBoundingBox(content) : envelope;
            adjustedEnvelope = encapsulating(adjustedEnvelope, content);
        } catch (ArithmeticException exception) {
            adjustedEnvelope = maximumReferenceBounds(start.getChunkPos(), content);
            arithmeticClipped = true;
        }
        BoundingBox referencedEnvelope = clampReferenceRange(start.getChunkPos(), adjustedEnvelope);
        if (arithmeticClipped || !sameHorizontalBounds(adjustedEnvelope, referencedEnvelope)) {
            warnClippedTerrain(start, source, structureKey);
            return referencedEnvelope;
        }
        return adjustedEnvelope;
    }

    public static boolean contentFitsReferenceRange(StructureStart start) {
        try {
            return fitsReferenceRange(start.getChunkPos(), contentBounds(start));
        } catch (UnrepresentableContentException exception) {
            return false;
        }
    }

    public static BoundingBox contentBounds(StructureStart start) {
        return contentBounds(start.getPieces());
    }

    private static BoundingBox contentBounds(List<StructurePiece> pieces) {
        BoundingBox bounds = null;
        for (StructurePiece piece : pieces) {
            bounds = bounds == null
                    ? copy(piece.getBoundingBox())
                    : bounds.encapsulate(piece.getBoundingBox());
        }
        if (bounds == null) {
            throw new IllegalStateException("Native jigsaw generated no content pieces");
        }
        return bounds;
    }

    private static BoundingBox clampReferenceRange(ChunkPos startChunk, BoundingBox envelope) {
        int minX = minimumReferenceBlock(startChunk.x());
        int maxX = maximumReferenceBlock(startChunk.x());
        int minZ = minimumReferenceBlock(startChunk.z());
        int maxZ = maximumReferenceBlock(startChunk.z());
        return new BoundingBox(
                Math.max(envelope.minX(), minX),
                envelope.minY(),
                Math.max(envelope.minZ(), minZ),
                Math.min(envelope.maxX(), maxX),
                envelope.maxY(),
                Math.min(envelope.maxZ(), maxZ)
        );
    }

    private static BoundingBox maximumReferenceBounds(ChunkPos startChunk, BoundingBox content) {
        return new BoundingBox(
                minimumReferenceBlock(startChunk.x()), content.minY(),
                minimumReferenceBlock(startChunk.z()),
                maximumReferenceBlock(startChunk.x()), content.maxY(),
                maximumReferenceBlock(startChunk.z())
        );
    }

    private static boolean fitsReferenceRange(ChunkPos startChunk, BoundingBox content) {
        return content.minX() >= minimumReferenceBlock(startChunk.x())
                && content.maxX() <= maximumReferenceBlock(startChunk.x())
                && content.minZ() >= minimumReferenceBlock(startChunk.z())
                && content.maxZ() <= maximumReferenceBlock(startChunk.z());
    }

    private static int minimumReferenceBlock(int originChunk) {
        return checkedReferenceBlock(
                ((long) originChunk - MAX_REFERENCE_DISTANCE_CHUNKS) << 4, originChunk);
    }

    private static int maximumReferenceBlock(int originChunk) {
        return checkedReferenceBlock(
                (((long) originChunk + MAX_REFERENCE_DISTANCE_CHUNKS) << 4) + 15L, originChunk);
    }

    private static int checkedReferenceBlock(long block, int originChunk) {
        if (block < Integer.MIN_VALUE || block > Integer.MAX_VALUE) {
            throw new UnrepresentableContentException("Native structure origin chunk " + originChunk
                    + " exceeds Minecraft's block coordinate range");
        }
        return (int) block;
    }

    private static boolean sameHorizontalBounds(BoundingBox left, BoundingBox right) {
        return left.minX() == right.minX()
                && left.minZ() == right.minZ()
                && left.maxX() == right.maxX()
                && left.maxZ() == right.maxZ();
    }

    private static BoundingBox copy(BoundingBox bounds) {
        return new BoundingBox(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private static BoundingBox encapsulating(BoundingBox left, BoundingBox right) {
        return new BoundingBox(
                Math.min(left.minX(), right.minX()),
                Math.min(left.minY(), right.minY()),
                Math.min(left.minZ(), right.minZ()),
                Math.max(left.maxX(), right.maxX()),
                Math.max(left.maxY(), right.maxY()),
                Math.max(left.maxZ(), right.maxZ())
        );
    }

    private static void warnClippedTerrain(
            StructureStart start, Structure source, String structureKey) {
        String key = warningKey(source, structureKey);
        if (WARNED_CLIPPED_TERRAIN.add(key)) {
            IrisLogging.warn("Clipping optional terrain envelope for native structure '"
                    + key + "' at " + start.getChunkPos().x() + "," + start.getChunkPos().z()
                    + " to Minecraft's " + MAX_REFERENCE_DISTANCE_CHUNKS
                    + "-chunk reference range; all generated content remains exactly referenced");
        }
    }

    private static void warnSkippedContent(
            StructureStart start, Structure source, String structureKey) {
        String key = warningKey(source, structureKey);
        if (WARNED_SKIPPED_CONTENT.add(key)) {
            IrisLogging.warn("Skipping native structure '" + key + "' at "
                    + start.getChunkPos().x() + "," + start.getChunkPos().z()
                    + " because its generated content exceeds Minecraft's "
                    + MAX_REFERENCE_DISTANCE_CHUNKS
                    + "-chunk reference range; no structure start, ownership, references, or blocks were published");
        }
    }

    private static String warningKey(Structure source, String structureKey) {
        if (structureKey != null && !structureKey.isBlank()) {
            return structureKey.trim().toLowerCase(Locale.ROOT);
        }
        return source.getClass().getName();
    }

    public static final class UnrepresentableContentException extends IllegalStateException {
        private UnrepresentableContentException(String message) {
            super(message);
        }
    }
}
