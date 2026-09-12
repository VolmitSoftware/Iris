package art.arcane.iris.nativegen;

import art.arcane.iris.structure.nativegen.NativeStructureOwnershipRecord;
import art.arcane.iris.structure.nativegen.NativeStructurePlacementPlanner;
import art.arcane.iris.structure.nativegen.NativeStructureStartPlan;
import art.arcane.iris.structure.placement.StructurePlacementGrid;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class NativeStructureOwnershipFingerprint {
    private static final Comparator<PieceIdentity> PIECE_ORDER = Comparator
            .comparing(PieceIdentity::type)
            .thenComparingInt(PieceIdentity::minX)
            .thenComparingInt(PieceIdentity::minY)
            .thenComparingInt(PieceIdentity::minZ)
            .thenComparingInt(PieceIdentity::maxX)
            .thenComparingInt(PieceIdentity::maxY)
            .thenComparingInt(PieceIdentity::maxZ)
            .thenComparingInt(PieceIdentity::orientation)
            .thenComparingInt(PieceIdentity::generationDepth)
            .thenComparing(PieceIdentity::detail);

    private NativeStructureOwnershipFingerprint() {
    }

    public static NativeStructureOwnershipRecord capture(String structureKey,
                                                         StructureStart start,
                                                         NativeStructureStartPlan plan,
                                                         BoundingBox referenceEnvelope) {
        StructureStart resolvedStart = requireValid(start);
        NativeStructureStartPlan resolvedPlan = Objects.requireNonNull(
                plan, "Native structure start plan must not be null");
        BoundingBox content = NativeStructureReferenceEnvelope.contentBounds(resolvedStart);
        BoundingBox references = Objects.requireNonNull(
                referenceEnvelope, "Native structure reference envelope must not be null");
        ChunkPos origin = resolvedStart.getChunkPos();
        return NativeStructureOwnershipRecord.create(
                structureKey,
                origin.x(),
                origin.z(),
                StructurePlacementGrid.placementIdentity(resolvedPlan.placement()),
                resolvedPlan.baseY(),
                content.minX(),
                content.minY(),
                content.minZ(),
                content.maxX(),
                content.maxY(),
                content.maxZ(),
                locatorY(resolvedStart),
                references.minX() >> 4,
                references.maxX() >> 4,
                references.minZ() >> 4,
                references.maxZ() >> 4,
                fingerprint(structureKey, resolvedStart),
                NativeStructurePlacementPlanner.decisionFor(resolvedPlan)
        );
    }

    public static boolean matches(NativeStructureOwnershipRecord record, StructureStart start) {
        if (record == null || start == null || !start.isValid()) {
            return false;
        }
        ChunkPos origin = start.getChunkPos();
        if (origin.x() != record.originChunkX()
                || origin.z() != record.originChunkZ()
                || !fingerprint(record.structureKey(), start).equals(record.contentFingerprint())) {
            return false;
        }
        BoundingBox content = NativeStructureReferenceEnvelope.contentBounds(start);
        if (content.getXSpan() != (record.contentMaxX() - record.contentMinX()) + 1
                || content.getYSpan() != (record.contentMaxY() - record.contentMinY()) + 1
                || content.getZSpan() != (record.contentMaxZ() - record.contentMinZ()) + 1) {
            return false;
        }
        return start.getStructure() instanceof OceanMonumentStructure
                || content.minX() == record.contentMinX()
                && content.minY() == record.contentMinY()
                && content.minZ() == record.contentMinZ()
                && content.maxX() == record.contentMaxX()
                && content.maxY() == record.contentMaxY()
                && content.maxZ() == record.contentMaxZ();
    }

    public static String fingerprint(String structureKey, StructureStart start) {
        StructureStart resolvedStart = requireValid(start);
        MessageDigest digest = sha256();
        updateString(digest, Objects.requireNonNull(structureKey,
                "Native structure key must not be null").trim().toLowerCase(Locale.ROOT));
        updateInt(digest, resolvedStart.getChunkPos().x());
        updateInt(digest, resolvedStart.getChunkPos().z());
        BoundingBox content = NativeStructureReferenceEnvelope.contentBounds(resolvedStart);
        List<PieceIdentity> pieces = contentPieces(resolvedStart, content.minY());
        updateInt(digest, pieces.size());
        for (PieceIdentity piece : pieces) {
            updateString(digest, piece.type());
            updateInt(digest, piece.minX());
            updateInt(digest, piece.minY());
            updateInt(digest, piece.minZ());
            updateInt(digest, piece.maxX());
            updateInt(digest, piece.maxY());
            updateInt(digest, piece.maxZ());
            updateInt(digest, piece.orientation());
            updateInt(digest, piece.generationDepth());
            updateString(digest, piece.detail());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static int locatorY(StructureStart start) {
        StructureStart resolvedStart = requireValid(start);
        if (!resolvedStart.getPieces().isEmpty()) {
            return resolvedStart.getPieces().getFirst().getLocatorPosition().getY();
        }
        throw new IllegalStateException("Native structure contains no locatable content pieces");
    }

    private static List<PieceIdentity> contentPieces(StructureStart start, int contentMinY) {
        List<PieceIdentity> pieces = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox bounds = piece.getBoundingBox();
            Identifier type = BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.getType());
            Direction orientation = piece.getOrientation();
            String detail = pieceDetail(piece, contentMinY);
            pieces.add(new PieceIdentity(
                    type == null ? "" : type.toString(),
                    bounds.minX(), bounds.minY() - contentMinY, bounds.minZ(),
                    bounds.maxX(), bounds.maxY() - contentMinY, bounds.maxZ(),
                    orientation == null ? -1 : orientation.get2DDataValue(),
                    piece.getGenDepth(),
                    detail
            ));
        }
        if (pieces.isEmpty()) {
            throw new IllegalStateException("Native structure contains no content pieces");
        }
        pieces.sort(PIECE_ORDER);
        return List.copyOf(pieces);
    }

    private static String pieceDetail(StructurePiece piece, int contentMinY) {
        if (!(piece instanceof PoolElementStructurePiece poolPiece)) {
            return "";
        }
        return poolElementIdentity(poolPiece.getElement()) + "|"
                + poolPiece.getPosition().getX() + ","
                + (poolPiece.getPosition().getY() - contentMinY) + ","
                + poolPiece.getPosition().getZ() + "|"
                + poolPiece.getGroundLevelDelta() + "|"
                + poolPiece.getRotation().getSerializedName();
    }

    private static String poolElementIdentity(StructurePoolElement element) {
        Identifier type = BuiltInRegistries.STRUCTURE_POOL_ELEMENT.getKey(element.getType());
        StringBuilder identity = new StringBuilder(element.getClass().getName())
                .append('|')
                .append(type == null ? "" : type)
                .append('|')
                .append(element.getProjection().getSerializedName())
                .append('|')
                .append(element.getGroundLevelDelta());
        if (element == EmptyPoolElement.INSTANCE) {
            return identity.append("|empty").toString();
        }
        if (element instanceof SinglePoolElement single) {
            try {
                return identity.append("|template=")
                        .append(single.getTemplateLocation()).toString();
            } catch (RuntimeException inlineTemplate) {
                return identity.append("|runtime-template").toString();
            }
        }
        if (element instanceof ListPoolElement list) {
            identity.append("|list[");
            for (StructurePoolElement child : list.getElements()) {
                String childIdentity = poolElementIdentity(child);
                identity.append(childIdentity.length()).append(':').append(childIdentity);
            }
            return identity.append(']').toString();
        }
        return identity.append("|bounded-custom").toString();
    }

    private static StructureStart requireValid(StructureStart start) {
        StructureStart resolved = Objects.requireNonNull(
                start, "Native structure start must not be null");
        if (!resolved.isValid()) {
            throw new IllegalArgumentException("Native structure start must be valid");
        }
        return resolved;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, encoded.length);
        digest.update(encoded);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private record PieceIdentity(String type,
                                 int minX, int minY, int minZ,
                                 int maxX, int maxY, int maxZ,
                                 int orientation, int generationDepth, String detail) {
    }
}
