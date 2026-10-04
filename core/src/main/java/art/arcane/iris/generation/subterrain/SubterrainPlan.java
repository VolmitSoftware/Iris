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

    SubterrainPlan(Options options) {
        definition = Objects.requireNonNull(options.definition());
        centerX = options.centerX();
        centerY = options.centerY();
        centerZ = options.centerZ();
        rotated = options.rotated();
        id = options.id();
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
            int path = definition.radius() - 4;
            anchor = occupiedAnchor(rotated ? centerX + path : centerX, y,
                    rotated ? centerZ : centerZ + path);
            if (anchor != null) {
                return anchor;
            }
            int firstTerrace = -definition.length() / 2 + 1;
            anchor = occupiedAnchor(rotated ? centerX + path : centerX + firstTerrace, y,
                    rotated ? centerZ + firstTerrace : centerZ + path);
            if (anchor != null) {
                return anchor;
            }
        }
        throw new IllegalStateException("Subterrain plan has no occupied anchor: " + id);
    }

    public SubterrainCell sample(int x, int y, int z) {
        if (!bounds.contains(x, y, z)) {
            return SubterrainCell.OUTSIDE;
        }
        int u = rotated ? z - centerZ : x - centerX;
        int v = rotated ? x - centerX : z - centerZ;
        Column column = switch (family()) {
            case CENOTE -> cenote(u, v);
            case TECTONIC_FAULT -> fault(u, v);
            case LAVA_TUBE -> tube(u, v, y);
            case TRAVERTINE_TERRACES -> terraces(u, v);
        };
        if (column == null || y < column.floor() - 2 || y > column.ceiling() + 2) {
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
        if (spacing > 0 && Math.abs(Math.floorMod(u + spacing / 2, spacing) - spacing / 2) <= 1
                && Math.abs(Math.floorMod(v + spacing / 2, spacing) - spacing / 2) <= 1) {
            return true;
        }
        if (column.head() >= y) {
            return false;
        }
        int cellU = Math.floorDiv(u, 12);
        int cellV = Math.floorDiv(v, 12);
        long hash = SubterrainPlanner.mix(definition.seedSalt() ^ cellU * 73428767L ^ cellV * 912931L);
        int anchorU = cellU * 12 + 3 + (int) Math.floorMod(hash, 6);
        int anchorV = cellV * 12 + 3 + (int) Math.floorMod(hash >>> 8, 6);
        int distance = Math.max(Math.abs(u - anchorU), Math.abs(v - anchorV));
        int height = (int) Math.floor((column.ceiling() - column.floor() - 1) * definition.formationFraction());
        int taperHeight = Math.max(0, height - distance * Math.max(1, height / 3));
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
        boolean rim = definition.fluidDepth() > 0 && ceiling <= head + 2;
        return new Column(baseY(), ceiling, head, distance < radius, rim,
                radius - distance, Math.abs(v), 0);
    }

    private Column fault(int u, int v) {
        int halfLength = definition.length() / 2;
        int radius = definition.radius();
        if (Math.abs(u) > halfLength + 2 || Math.abs(v) > radius + 2) {
            return null;
        }
        int step = Math.max(2, radius / 4);
        int shelf = Math.max(0, (Math.abs(v) - radius / 2) / step) * Math.max(2, definition.height() / 8);
        return new Column(baseY() + shelf, baseY() + definition.height(), baseY() - 1,
                Math.abs(u) < halfLength && Math.abs(v) < radius, false,
                Math.min(halfLength - Math.abs(u), radius - Math.abs(v)), Math.abs(v), 0);
    }

    private Column tube(int u, int v, int y) {
        int halfLength = definition.length() / 2;
        int path = (int) Math.round(Math.sin(u * Math.PI / Math.max(1, halfLength)) * definition.radius() * 0.15);
        int width = Math.max(6, (int) Math.floor(definition.radius() * 0.8));
        int offset = v - path;
        if (Math.abs(u) > halfLength + 2 || Math.abs(offset) > width + 2) {
            return null;
        }
        double arch = Math.sqrt(Math.max(0, 1 - Math.pow(Math.min(1, Math.abs(offset) / (double) width), 2)));
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
        return new Column(floor, ceiling, head, Math.abs(u) < halfLength && Math.abs(offset) < width,
                rim, Math.min(halfLength - Math.abs(u), width - Math.abs(offset)), Math.abs(offset), path);
    }

    private Column terraces(int u, int v) {
        int halfLength = definition.length() / 2;
        int radius = definition.radius();
        if (Math.abs(u) > halfLength + 2 || Math.abs(v) > radius + 2) {
            return null;
        }
        int segment = Math.max(4, definition.length() / definition.terraceCount());
        int index = Math.max(0, Math.min(definition.terraceCount() - 1, Math.floorDiv(u + halfLength, segment)));
        int rise = Math.max(1, (definition.height() / 3) / definition.terraceCount());
        int floor = baseY() + index * rise;
        int head = floor + definition.fluidDepth();
        int local = Math.floorMod(u + halfLength, segment);
        boolean rim = definition.fluidDepth() > 0 && (local <= 1 || local >= segment - 2 || Math.abs(v) >= radius - 2);
        int ceiling = baseY() + definition.height();
        if (rim) {
            floor = head + 1;
        }
        double pathDistance = Math.abs(Math.abs(v) - (radius - 4));
        if (pathDistance <= 2) {
            floor = Math.max(floor, head + 1);
        }
        return new Column(floor, ceiling, head, Math.abs(u) < halfLength && Math.abs(v) < radius,
                false, Math.min(halfLength - Math.abs(u), radius - Math.abs(v)), pathDistance,
                v < 0 ? -radius + 4 : radius - 4);
    }

    private SubterrainPosition occupiedAnchor(int x, int preferredY, int z) {
        if (sample(x, preferredY, z).occupied()) {
            return new SubterrainPosition(x, preferredY, z);
        }
        for (int candidateY = bounds.minY(); candidateY <= bounds.maxY(); candidateY++) {
            if (sample(x, candidateY, z).occupied()) {
                return new SubterrainPosition(x, candidateY, z);
            }
        }
        return null;
    }

    private int baseY() {
        return centerY - definition.height() / 2;
    }

    record Options(SubterrainPlanner.Definition definition, int centerX, int centerY, int centerZ,
                   boolean rotated, String id) {
    }

    private record Column(int floor, int ceiling, int head, boolean interior, boolean rim,
                          double boundary, double pathDistance, int pathOffset) {
    }
}
