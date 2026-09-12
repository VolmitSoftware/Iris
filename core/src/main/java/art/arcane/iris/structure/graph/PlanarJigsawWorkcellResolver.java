package art.arcane.iris.structure.graph;

import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcell;
import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcellArchetype;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.placement.IrisStructure;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class PlanarJigsawWorkcellResolver {
    public static final int DEFAULT_SIZE = 16;
    public static final int MAX_WIDTH = 128;
    public static final int MAX_HEIGHT = 192;
    public static final int MAX_DEPTH = 128;
    public static final long MAX_VOLUME = 2_097_152L;

    private PlanarJigsawWorkcellResolver() {
    }

    public static Map<IrisJigsawWorkcellArchetype, ResolvedWorkcell> resolve(IrisStructure structure) {
        IrisStructure source = Objects.requireNonNull(structure, "Planar jigsaw structure");
        EnumMap<IrisJigsawWorkcellArchetype, ResolvedWorkcell> resolved =
                new EnumMap<>(IrisJigsawWorkcellArchetype.class);
        if (source.getPlanarWorkcells() == null || source.getPlanarWorkcells().isEmpty()) {
            IrisPosition fallback = source.getCellSize() == null
                    ? new IrisPosition(DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE)
                    : source.getCellSize();
            for (IrisJigsawWorkcellArchetype archetype : IrisJigsawWorkcellArchetype.values()) {
                ResolvedWorkcell workcell = new ResolvedWorkcell(
                        archetype,
                        "",
                        fallback.getX(),
                        fallback.getY(),
                        fallback.getZ(),
                        true);
                validate(workcell);
                resolved.put(archetype, workcell);
            }
            return Collections.unmodifiableMap(resolved);
        }

        for (IrisJigsawWorkcell configured : source.getPlanarWorkcells()) {
            if (configured == null || configured.getArchetype() == null) {
                throw new IllegalArgumentException("Planar jigsaw workcells require an archetype");
            }
            ResolvedWorkcell workcell = new ResolvedWorkcell(
                    configured.getArchetype(),
                    normalizeDisplayName(configured.getDisplayName()),
                    configured.getWidth(),
                    configured.getHeight(),
                    configured.getDepth(),
                    configured.isEnabled());
            validate(workcell);
            if (resolved.putIfAbsent(workcell.archetype(), workcell) != null) {
                throw new IllegalArgumentException(
                        "Planar jigsaw workcell " + workcell.archetype() + " is configured more than once");
            }
        }
        if (resolved.size() != IrisJigsawWorkcellArchetype.values().length) {
            for (IrisJigsawWorkcellArchetype archetype : IrisJigsawWorkcellArchetype.values()) {
                if (!resolved.containsKey(archetype)) {
                    throw new IllegalArgumentException("Planar jigsaw workcell " + archetype + " is missing");
                }
            }
        }
        return Collections.unmodifiableMap(resolved);
    }

    public static ResolvedWorkcell workcell(
            Map<IrisJigsawWorkcellArchetype, ResolvedWorkcell> workcells,
            IrisJigsawPiece piece
    ) {
        Map<IrisJigsawWorkcellArchetype, ResolvedWorkcell> resolved = Objects.requireNonNull(
                workcells,
                "Planar jigsaw workcells");
        IrisJigsawWorkcellArchetype archetype = IrisJigsawWorkcellArchetype.fromPiece(piece);
        ResolvedWorkcell workcell = resolved.get(archetype);
        if (workcell == null) {
            throw new IllegalArgumentException("Planar jigsaw workcell " + archetype + " is not configured");
        }
        return workcell;
    }

    public static IrisPosition canonicalDimensions(IrisJigsawPiece piece, IrisObject object) {
        IrisJigsawPiece sourcePiece = Objects.requireNonNull(piece, "Planar jigsaw piece");
        IrisObject sourceObject = Objects.requireNonNull(object, "Planar jigsaw object");
        IrisJigsawWorkcellArchetype archetype = IrisJigsawWorkcellArchetype.fromPiece(sourcePiece);
        int quarterTurns = archetype.sourceToCanonicalQuarterTurns(sourcePiece);
        return Math.floorMod(quarterTurns, 2) == 0
                ? new IrisPosition(sourceObject.getW(), sourceObject.getH(), sourceObject.getD())
                : new IrisPosition(sourceObject.getD(), sourceObject.getH(), sourceObject.getW());
    }

    public record ResolvedWorkcell(
            IrisJigsawWorkcellArchetype archetype,
            String displayName,
            int width,
            int height,
            int depth,
            boolean enabled
    ) {
        public ResolvedWorkcell {
            Objects.requireNonNull(archetype, "Planar jigsaw workcell archetype");
            displayName = normalizeDisplayName(displayName);
        }

        public IrisPosition dimensions() {
            return new IrisPosition(width, height, depth);
        }

        public boolean contains(IrisPosition dimensions) {
            IrisPosition size = Objects.requireNonNull(dimensions, "Planar jigsaw object dimensions");
            return size.getX() <= width && size.getY() <= height && size.getZ() <= depth;
        }
    }

    private static String normalizeDisplayName(String displayName) {
        return displayName == null ? "" : displayName.trim();
    }

    private static void validate(ResolvedWorkcell workcell) {
        if (workcell.width() < 3 || workcell.width() > MAX_WIDTH
                || workcell.height() < 1 || workcell.height() > MAX_HEIGHT
                || workcell.depth() < 3 || workcell.depth() > MAX_DEPTH) {
            throw new IllegalArgumentException("Planar jigsaw workcell " + workcell.archetype()
                    + " dimensions must keep width/depth within 3..128 and height within 1..192");
        }
        long volume = (long) workcell.width() * workcell.height() * workcell.depth();
        if (volume > MAX_VOLUME) {
            throw new IllegalArgumentException("Planar jigsaw workcell " + workcell.archetype()
                    + " volume cannot exceed " + MAX_VOLUME + " blocks");
        }
    }
}
