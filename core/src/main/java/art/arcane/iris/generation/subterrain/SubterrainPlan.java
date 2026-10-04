package art.arcane.iris.generation.subterrain;

import java.util.Objects;

public final class SubterrainPlan {
    private final SubterrainPlanner.Definition definition;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final boolean rotated;
    private final String id;
    private final SubterrainBounds bounds;
    private final double shapePhase;
    private final ThreadLocal<ColumnCache> columnCache = ThreadLocal.withInitial(ColumnCache::new);

    SubterrainPlan(Options options) {
        definition = Objects.requireNonNull(options.definition());
        centerX = options.centerX();
        centerY = options.centerY();
        centerZ = options.centerZ();
        rotated = options.rotated();
        id = options.id();
        shapePhase = (SubterrainPlanner.mix(id.hashCode() ^ definition.seedSalt()) >>> 11) * 0x1.0p-53 * Math.PI * 2;
        int along = family() == IrisSubterrainFamily.CENOTE ? definition.radius() : definition.length() / 2;
        int across = definition.radius();
        int extentX = (rotated ? across : along) + 2;
        int extentZ = (rotated ? along : across) + 2;
        bounds = new SubterrainBounds(centerX - extentX, baseY() - 2, centerZ - extentZ,
                centerX + extentX, baseY() + definition.height() + 2
                + (family() == IrisSubterrainFamily.LAVA_TUBE ? definition.chimneyHeight() : 0), centerZ + extentZ);
    }

    public String id() {
        return id;
    }

    public IrisSubterrainFamily family() {
        return definition.family();
    }

    public String biome() {
        return definition.biome();
    }

    public int priority() {
        return definition.priority();
    }

    public SubterrainBounds bounds() {
        return bounds;
    }

    public int centerX() {
        return centerX;
    }

    public int centerY() {
        return centerY;
    }

    public int centerZ() {
        return centerZ;
    }

    public SubterrainPosition anchor() {
        int y = family() == IrisSubterrainFamily.LAVA_TUBE ? centerY : baseY() + definition.fluidDepth() + 2;
        SubterrainPosition anchor = occupiedAnchor(centerX, y, centerZ);
        if (anchor != null) {
            return anchor;
        }
        if (family() == IrisSubterrainFamily.TRAVERTINE_TERRACES) {
            Column center = cachedColumn(0, 0);
            if (center != null) {
                int path = center.pathOffset();
                anchor = occupiedAnchor(rotated ? centerX + path : centerX, y,
                        rotated ? centerZ : centerZ + path);
                if (anchor != null) {
                    return anchor;
                }
            }
        }
        for (int u = -definition.length() / 2; u <= definition.length() / 2; u++) {
            for (int v = -definition.radius(); v <= definition.radius(); v++) {
                int x = rotated ? centerX + v : centerX + u;
                int z = rotated ? centerZ + u : centerZ + v;
                Column column = family() == IrisSubterrainFamily.LAVA_TUBE ? tube(u, v, centerY) : cachedColumn(u, v);
                if (column == null || column.pathDistance() > 2) {
                    continue;
                }
                int candidateY = Math.max(column.floor(), column.head()) + 2;
                SubterrainCell cell = sample(x, candidateY, z);
                if (cell.occupied() && cell.room().reservedPassage()) {
                    return new SubterrainPosition(x, candidateY, z);
                }
            }
        }
        throw new IllegalStateException("Subterrain plan has no occupied anchor: " + id);
    }

    public SubterrainPosition dryLandingAt(int x, int z) {
        int u = rotated ? z - centerZ : x - centerX;
        int v = rotated ? x - centerX : z - centerZ;
        Column column = family() == IrisSubterrainFamily.LAVA_TUBE ? tube(u, v, centerY) : cachedColumn(u, v);
        if (column == null) {
            return null;
        }
        for (int y = Math.max(bounds.minY() + 1, column.floor() + 1);
             y < Math.min(bounds.maxY(), column.ceiling() - 1); y++) {
            if (sample(x, y, z).kind() == SubterrainCell.Kind.AIR
                    && sample(x, y + 1, z).kind() == SubterrainCell.Kind.AIR
                    && sample(x, y - 1, z).solid()) {
                return new SubterrainPosition(x, y, z);
            }
        }
        return null;
    }

    public SubterrainCell sample(int x, int y, int z) {
        if (!bounds.contains(x, y, z)) {
            return SubterrainCell.OUTSIDE;
        }
        int u = rotated ? z - centerZ : x - centerX;
        int v = rotated ? x - centerX : z - centerZ;
        Column column = family() == IrisSubterrainFamily.LAVA_TUBE ? tube(u, v, y) : cachedColumn(u, v);
        if (column == null) {
            return SubterrainCell.OUTSIDE;
        }
        int sealFloor = family() == IrisSubterrainFamily.CENOTE ? baseY() : column.floor();
        if (y < sealFloor - 2 || y > column.ceiling() + 2) {
            return SubterrainCell.OUTSIDE;
        }
        SubterrainCell.Kind kind = classify(column, u, v, y);
        int pathX = rotated ? centerX + column.pathOffset() : x;
        int pathZ = rotated ? z : centerZ + column.pathOffset();
        boolean passage = kind == SubterrainCell.Kind.AIR && column.pathDistance() <= 2.0;
        SubterrainRoom room = new SubterrainRoom(id, biome(), family(), centerX, centerY, centerZ,
                pathX, Math.max(column.floor() + 2, column.head() + 2), pathZ,
                column.floor(), column.ceiling(), column.boundary(), column.head(), passage,
                kind == SubterrainCell.Kind.SOLID, kind);
        String material = switch (kind) {
            case SOLID -> definition.solid();
            case AIR -> "minecraft:cave_air";
            case WATER -> "minecraft:water";
            case LAVA -> "minecraft:lava";
            case OUTSIDE -> "";
        };
        return new SubterrainCell(kind, material, room);
    }

    private Column cachedColumn(int u, int v) {
        ColumnCache cache = columnCache.get();
        long key = ((long) u << 32) ^ (v & 0xffffffffL);
        for (int i = 0; i < cache.count; i++) {
            if (cache.keys[i] == key) {
                return cache.columns[i];
            }
        }
        Column column = switch (family()) {
            case CENOTE -> cenote(u, v);
            case TECTONIC_FAULT -> fault(u, v);
            case TRAVERTINE_TERRACES -> terraces(u, v);
            case LAVA_TUBE -> throw new IllegalStateException("Tube columns depend on height");
        };
        int slot = cache.next;
        cache.keys[slot] = key;
        cache.columns[slot] = column;
        cache.next = (slot + 1) % cache.keys.length;
        cache.count = Math.min(cache.keys.length, cache.count + 1);
        return column;
    }

    private SubterrainCell.Kind classify(Column column, int u, int v, int y) {
        if (!column.interior() || y <= column.floor() || y >= column.ceiling() || column.rim()) {
            return SubterrainCell.Kind.SOLID;
        }
        if (formation(column, u, v, y)) {
            return SubterrainCell.Kind.SOLID;
        }
        if (y <= column.head()) {
            return switch (definition.fluid()) {
                case WATER -> SubterrainCell.Kind.WATER;
                case LAVA -> SubterrainCell.Kind.LAVA;
            };
        }
        return SubterrainCell.Kind.AIR;
    }

    private boolean formation(Column column, int u, int v, int y) {
        if (column.pathDistance() <= 3 || column.boundary() < 4) {
            return false;
        }
        int spacing = definition.pillarSpacing();
        if (spacing > 0) {
            int pillarU = Math.floorDiv(u + spacing / 2, spacing);
            int pillarV = Math.floorDiv(v + spacing / 2, spacing);
            long pillarHash = SubterrainPlanner.mix(definition.seedSalt() ^ id.hashCode()
                    ^ pillarU * 73428767L ^ pillarV * 912931L);
            int jitter = Math.max(2, spacing / 5);
            int offsetU = u - pillarU * spacing - (int) Math.floorMod(pillarHash, jitter * 2 + 1) + jitter;
            int offsetV = v - pillarV * spacing - (int) Math.floorMod(pillarHash >>> 8, jitter * 2 + 1) + jitter;
            double relativeHeight = (y - column.floor()) / (double) Math.max(1, column.ceiling() - column.floor());
            double taper = relativeHeight * 2 - 1;
            double radius = 1.3 + 1.2 * taper * taper;
            double leanU = ((pillarHash >>> 16 & 255L) / 255.0 - 0.5) * 2;
            double leanV = ((pillarHash >>> 24 & 255L) / 255.0 - 0.5) * 2;
            double distanceU = offsetU - leanU * taper;
            double distanceV = offsetV - leanV * taper;
            if (distanceU * distanceU + distanceV * distanceV <= radius * radius) {
                return true;
            }
        }
        if (column.head() >= y) {
            return false;
        }
        int cellU = Math.floorDiv(u, 12);
        int cellV = Math.floorDiv(v, 12);
        long hash = SubterrainPlanner.mix(definition.seedSalt() ^ id.hashCode() ^ cellU * 73428767L ^ cellV * 912931L);
        int anchorU = cellU * 12 + 3 + (int) Math.floorMod(hash, 6);
        int anchorV = cellV * 12 + 3 + (int) Math.floorMod(hash >>> 8, 6);
        double distance = Math.hypot(u - anchorU, v - anchorV);
        int height = (int) Math.floor((column.ceiling() - column.floor() - 1) * definition.formationFraction());
        int taperHeight = Math.max(0, (int) Math.floor(height - distance * Math.max(1, height / 3)));
        return distance <= 2 && taperHeight > 0
                && (y <= column.floor() + taperHeight || y >= column.ceiling() - taperHeight);
    }

    private Column cenote(int u, int v) {
        double distance = Math.hypot(u, v);
        int radius = definition.radius();
        if (distance > radius + 2) {
            return null;
        }
        double normalized = Math.min(1, distance / radius);
        int ceiling = baseY() + Math.max(3, (int) Math.floor(definition.height() * Math.sqrt(1 - normalized * normalized)));
        int head = baseY() + definition.fluidDepth();
        double bank = Math.max(0, Math.min(1, (normalized - 0.65) / 0.25));
        double slope = bank * bank * (3 - 2 * bank);
        int floor = baseY() + (int) Math.round((definition.fluidDepth() + 2) * slope);
        boolean rim = definition.fluidDepth() > 0 && ceiling <= head + 2;
        return new Column(floor, ceiling, head, distance < radius, rim,
                radius - distance, Math.abs(v), 0);
    }

    private Column fault(int u, int v) {
        Footprint footprint = footprint(u, v);
        if (footprint == null) {
            return null;
        }
        int step = Math.max(2, definition.radius() / 4);
        int shelf = Math.max(0, (int) (Math.abs(footprint.offset()) - footprint.width() * 0.5) / step)
                * Math.max(2, definition.height() / 8);
        int ceiling = vaultedCeiling(u, footprint, baseY() + shelf, baseY() - 1);
        return new Column(baseY() + shelf, ceiling, baseY() - 1,
                footprint.boundary() > 0, false, footprint.boundary(), Math.abs(footprint.offset()), footprint.path());
    }

    private Column tube(int u, int v, int y) {
        int halfLength = definition.length() / 2;
        int path = (int) Math.round(Math.sin(u * Math.PI / Math.max(1, halfLength)) * definition.radius() * 0.15);
        int width = Math.max(6, (int) Math.floor(definition.radius() * 0.8));
        int offset = v - path;
        if (Math.abs(u) > halfLength + 2 || Math.abs(offset) > width + 2) {
            return null;
        }
        double normalizedAlong = Math.min(1, Math.abs(u / (double) Math.max(1, halfLength)));
        double endTaper = Math.sqrt(Math.max(0, 1 - Math.pow(normalizedAlong, 12)));
        double taperedWidth = width * endTaper;
        if (Math.abs(offset) > taperedWidth + 2) {
            return null;
        }
        double arch = Math.sqrt(Math.max(0, 1 - Math.pow(Math.min(1, Math.abs(offset) / Math.max(1, taperedWidth)), 2))) * endTaper;
        int floor = centerY - Math.max(2, (int) Math.floor(definition.height() * 0.5 * arch));
        int ceiling = centerY + Math.max(2, (int) Math.floor(definition.height() * 0.5 * arch));
        int head = baseY() + definition.fluidDepth();
        if (Math.abs(offset) >= width * 0.55) {
            floor = Math.max(floor, head + 2);
        }
        int chimneyAlong = Math.min(Math.abs(u - halfLength / 2), Math.abs(u + halfLength / 2));
        double chimneyRadius = Math.hypot(chimneyAlong, offset);
        if (definition.chimneyHeight() > 0 && chimneyRadius <= 5) {
            int tubeCeiling = ceiling;
            if (chimneyRadius < 3) {
                ceiling = baseY() + definition.height() + definition.chimneyHeight();
            } else if (y >= tubeCeiling - 1) {
                return new Column(tubeCeiling - 1, baseY() + definition.height() + definition.chimneyHeight(),
                        head, false, true, 5 - chimneyRadius, Math.abs(offset), path);
            }
        }
        boolean rim = definition.fluidDepth() > 0 && floor >= head && floor < head + 2;
        return new Column(floor, ceiling, head, Math.abs(u) < halfLength && Math.abs(offset) < taperedWidth,
                rim, Math.min(halfLength - Math.abs(u), taperedWidth - Math.abs(offset)), Math.abs(offset), path);
    }

    private Column terraces(int u, int v) {
        Footprint footprint = footprint(u, v);
        if (footprint == null) {
            return null;
        }
        double warped = u + definition.radius() * 0.2 * Math.sin(v / (double) Math.max(6, definition.radius()) * 2.4 + shapePhase)
                + definition.radius() * 0.08 * Math.sin(u / 29.0 + v / 19.0 + shapePhase);
        double segment = Math.max(4.0, definition.length() / (double) definition.terraceCount());
        double terrace = (warped + definition.length() / 2.0) / segment;
        int index = Math.max(0, Math.min(definition.terraceCount() - 1, (int) Math.floor(terrace)));
        int rise = Math.max(1, (definition.height() / 3) / definition.terraceCount());
        int floor = baseY() + index * rise;
        int head = floor + definition.fluidDepth();
        double local = (terrace - Math.floor(terrace)) * segment;
        boolean rim = definition.fluidDepth() > 0 && (local <= 1.5 || local >= segment - 1.5 || footprint.boundary() <= 2);
        double pathOffset = Math.copySign(Math.max(0, footprint.width() - 4), footprint.offset());
        double pathDistance = Math.abs(footprint.offset() - pathOffset);
        if (rim || pathDistance <= 2) {
            floor = Math.max(floor, head + 1);
        }
        int ceiling = vaultedCeiling(u, footprint, floor, head);
        return new Column(floor, ceiling, head, footprint.boundary() > 0,
                false, footprint.boundary(), pathDistance, footprint.path() + (int) Math.round(pathOffset));
    }

    private Profile profile(int u) {
        ColumnCache cache = columnCache.get();
        for (int i = 0; i < cache.profileCount; i++) {
            if (cache.profileKeys[i] == u) {
                return cache.profiles[i];
            }
        }
        double along = Math.min(1, Math.abs(u / (double) Math.max(1, definition.length() / 2)));
        double taper = Math.sqrt(Math.max(0, 1 - Math.pow(along, 6)));
        double width = definition.radius() * taper * (0.83 + 0.08 * Math.sin(u / 31.0 + shapePhase)
                + 0.05 * Math.sin(u / 13.0 + shapePhase * 1.7));
        double bend = family() == IrisSubterrainFamily.TECTONIC_FAULT ? 0.08 : 0.12;
        int path = (int) Math.round(definition.radius() * bend * Math.sin(u / 47.0 + shapePhase) * taper);
        double vaultWave = 0.87 + 0.07 * Math.sin(u / 37.0 + shapePhase) + 0.04 * Math.sin(u / 17.0 - shapePhase);
        Profile profile = new Profile(Math.min(width, definition.radius() - Math.abs(path)), path, vaultWave);
        int slot = cache.profileNext;
        cache.profileKeys[slot] = u;
        cache.profiles[slot] = profile;
        cache.profileNext = (slot + 1) % cache.profiles.length;
        cache.profileCount = Math.min(cache.profiles.length, cache.profileCount + 1);
        return profile;
    }

    private Footprint footprint(int u, int v) {
        int halfLength = definition.length() / 2;
        if (Math.abs(u) > halfLength + 2) {
            return null;
        }
        Profile profile = profile(u);
        double width = profile.width();
        int path = profile.path();
        double offset = v - path;
        double boundary = Math.min(halfLength - Math.abs(u), width - Math.abs(offset));
        if (boundary < -2) {
            return null;
        }
        return new Footprint(width, offset, boundary, path);
    }

    private int vaultedCeiling(int u, Footprint footprint, int floor, int head) {
        double cross = Math.min(1, Math.abs(footprint.offset()) / Math.max(1, footprint.width()));
        double arch = Math.sqrt(Math.max(0, 1 - cross * cross));
        double wave = profile(u).vaultWave();
        double roofVariation = 0.05 * Math.sin(u / 37.0 + footprint.offset() / 17.0 + shapePhase) * arch
                + 0.06 * footprint.offset() / Math.max(1, footprint.width()) * Math.sin(u / 41.0 - shapePhase);
        int ceiling = baseY() + (int) Math.floor(definition.height() * wave * (0.4 + 0.6 * arch + roofVariation));
        return Math.min(baseY() + definition.height(), Math.max(Math.max(floor, head) + 3, ceiling));
    }

    private SubterrainPosition occupiedAnchor(int x, int preferredY, int z) {
        if (anchorCell(sample(x, preferredY, z))) {
            return new SubterrainPosition(x, preferredY, z);
        }
        for (int candidateY = bounds.minY(); candidateY <= bounds.maxY(); candidateY++) {
            if (anchorCell(sample(x, candidateY, z))) {
                return new SubterrainPosition(x, candidateY, z);
            }
        }
        return null;
    }

    private boolean anchorCell(SubterrainCell cell) {
        return cell.occupied() && (family() != IrisSubterrainFamily.TRAVERTINE_TERRACES || cell.room().reservedPassage());
    }

    private int baseY() {
        return centerY - definition.height() / 2;
    }

    record Options(SubterrainPlanner.Definition definition, int centerX, int centerY, int centerZ,
                   boolean rotated, String id) {
    }

    private static final class ColumnCache {
        private final long[] keys = new long[8];
        private final Column[] columns = new Column[8];
        private final int[] profileKeys = new int[3];
        private final Profile[] profiles = new Profile[3];
        private int profileCount;
        private int profileNext;
        private int count;
        private int next;
    }

    private record Profile(double width, int path, double vaultWave) {
    }

    private record Footprint(double width, double offset, double boundary, int path) {
    }

    private record Column(int floor, int ceiling, int head, boolean interior, boolean rim,
                          double boundary, double pathDistance, int pathOffset) {
    }
}
