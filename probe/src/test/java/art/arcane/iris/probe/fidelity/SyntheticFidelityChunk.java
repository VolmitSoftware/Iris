package art.arcane.iris.probe.fidelity;

final class SyntheticFidelityChunk implements FidelityChunk {
    private static final int CANOPY_DEPTH = 3;

    private final int minY;
    private int surfaceY = 64;
    private int seaLevel = Integer.MIN_VALUE;
    private int biomeSplit = 16;
    private String westBiome = "plains";
    private String eastBiome = "forest";
    private int caveMinY;
    private int caveMaxY;
    private int caveColumns;
    private int treeColumns;
    private int treeHeight;
    private int canopyColumns;
    private int canopyY;
    private String canopyCap;
    private int roofColumns;
    private int roofMinY;
    private int roofMaxY;
    private int lavaMinY;
    private int lavaMaxY;
    private int lavaColumns;
    private int wallThickness;
    private int overhangColumns;
    private int overhangMinY;
    private int overhangMaxY;
    private boolean blocks = true;

    SyntheticFidelityChunk(int minY) {
        this.minY = minY;
    }

    SyntheticFidelityChunk surface(int y) {
        surfaceY = y;
        return this;
    }

    SyntheticFidelityChunk sea(int y) {
        seaLevel = y;
        return this;
    }

    SyntheticFidelityChunk biomes(String west, String east, int split) {
        westBiome = west;
        eastBiome = east;
        biomeSplit = split;
        return this;
    }

    SyntheticFidelityChunk caves(int fromY, int toY, int columns) {
        caveMinY = fromY;
        caveMaxY = toY;
        caveColumns = columns;
        return this;
    }

    SyntheticFidelityChunk trees(int columns, int height) {
        treeColumns = columns;
        treeHeight = height;
        return this;
    }

    SyntheticFidelityChunk canopy(int columns, int leavesY, String cap) {
        canopyColumns = columns;
        canopyY = leavesY;
        canopyCap = cap;
        return this;
    }

    SyntheticFidelityChunk roof(int columns, int fromY, int toY) {
        roofColumns = columns;
        roofMinY = fromY;
        roofMaxY = toY;
        return this;
    }

    SyntheticFidelityChunk lavaPool(int fromY, int toY, int columns) {
        lavaMinY = fromY;
        lavaMaxY = toY;
        lavaColumns = columns;
        return this;
    }

    SyntheticFidelityChunk walls(int thickness) {
        wallThickness = thickness;
        return this;
    }

    SyntheticFidelityChunk overhang(int columns, int fromY, int toY) {
        overhangColumns = columns;
        overhangMinY = fromY;
        overhangMaxY = toY;
        return this;
    }

    SyntheticFidelityChunk noBlocks() {
        blocks = false;
        return this;
    }

    @Override
    public int surfaceY(int localX, int localZ) {
        if (localX < roofColumns) {
            return roofMaxY;
        }
        if (localX < canopyColumns) {
            return canopyY + CANOPY_DEPTH;
        }
        return localX < treeColumns ? surfaceY + treeHeight : surfaceY;
    }

    @Override
    public String surfaceBiome(int localX, int localZ) {
        return localX < biomeSplit ? westBiome : eastBiome;
    }

    @Override
    public boolean open(int localX, int y, int localZ) {
        String id = id(localX, y, localZ);
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air");
    }

    @Override
    public boolean fluid(int localX, int y, int localZ) {
        String id = id(localX, y, localZ);
        return id.equals("minecraft:water") || id.equals("minecraft:lava");
    }

    @Override
    public String block(int localX, int y, int localZ) {
        return blocks ? id(localX, y, localZ) : null;
    }

    @Override
    public boolean terrainMass(int localX, int y, int localZ) {
        return y <= surfaceY && !overhang(localX, y);
    }

    @Override
    public boolean wallsKnown() {
        return wallThickness > 0;
    }

    @Override
    public boolean wall(int localX, int y, int localZ) {
        return localX >= caveColumns && localX < caveColumns + wallThickness && y >= caveMinY && y < caveMaxY;
    }

    private String id(int localX, int y, int localZ) {
        if (y < minY) {
            return "minecraft:void_air";
        }
        if (y <= surfaceY) {
            if (localX < lavaColumns && y >= lavaMinY && y < lavaMaxY) {
                return "minecraft:lava";
            }
            if (overhang(localX, y)) {
                return "minecraft:air";
            }
            boolean cave = localX < caveColumns && y >= caveMinY && y < caveMaxY;
            return cave ? "minecraft:cave_air" : y == surfaceY ? "minecraft:grass_block" : "minecraft:stone";
        }
        if (localX < roofColumns && y >= roofMinY && y <= roofMaxY) {
            return "minecraft:stone";
        }
        if (localX < canopyColumns && y >= canopyY && y <= canopyY + CANOPY_DEPTH) {
            return y == canopyY + CANOPY_DEPTH ? canopyCap : "minecraft:spruce_leaves";
        }
        if (localX < treeColumns && y <= surfaceY + treeHeight) {
            return y == surfaceY + treeHeight ? "minecraft:oak_leaves" : "minecraft:oak_log";
        }
        if (y <= seaLevel) {
            return "minecraft:water";
        }
        return "minecraft:air";
    }

    private boolean overhang(int localX, int y) {
        return localX < overhangColumns && y >= overhangMinY && y < overhangMaxY;
    }
}
