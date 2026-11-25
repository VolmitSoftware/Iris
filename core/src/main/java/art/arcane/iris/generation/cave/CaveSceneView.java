package art.arcane.iris.generation.cave;

import art.arcane.iris.generation.cave.field.CaveCarver;
import art.arcane.iris.generation.cave.field.CavePart;
import art.arcane.iris.generation.cave.field.CngNoiseFactory;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.studio.scene.SceneBlocks;
import art.arcane.iris.studio.scene.SceneEntry;
import art.arcane.iris.studio.scene.SceneRequest;
import art.arcane.iris.studio.scene.SceneSizes;
import art.arcane.iris.studio.scene.SceneView;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The space each cave of the pack carves under flat ground, as blocks coloured by depth. Each block names the part of
 * the cave that carved it, its tunnel or its cavern. Caves are only shown here; their files are edited elsewhere.
 */
public final class CaveSceneView implements SceneView {
    private static final SceneSizes SIZES = new SceneSizes(List.of(64, 128, 256), 128);
    private static final int VARIANTS = 4;
    private static final int VARIANT_SPACING_BLOCKS = 4096;
    private static final int SURFACE_ABOVE_SEA_BLOCKS = 48;
    private static final int MAX_DEPTH_BLOCKS = 160;
    private static final int CHUNK_BLOCKS = 16;
    private static final int CHUNK_COLUMNS = CHUNK_BLOCKS * CHUNK_BLOCKS;
    private static final byte ROCK = 0;
    private static final String GROUP = "Caves";
    private static final String[] DEPTH_BANDS = {"minecraft:yellow_concrete", "minecraft:lime_concrete", "minecraft:light_blue_concrete",
            "minecraft:purple_concrete"};
    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    @Override
    public String id() {
        return "caves";
    }

    @Override
    public String label() {
        return "Caves";
    }

    @Override
    public String description() {
        return "The space each cave of the pack carves under flat ground, its tunnel and its cavern";
    }

    @Override
    public SceneSizes sizes() {
        return SIZES;
    }

    @Override
    public List<SceneEntry> entries(Engine engine) {
        return Arrays.stream(engine.getData().getCaveLoader().getPossibleKeys()).sorted()
                .map(key -> new SceneEntry(key, key, GROUP, VARIANTS))
                .toList();
    }

    @Override
    public SceneBlocks scene(Engine engine, SceneRequest request) {
        IrisCave cave = caveOf(engine.getData(), request.key());
        int surfaceY = Math.min(engine.getHeight() - 1, engine.getDimension().getFluidHeight() + SURFACE_ABOVE_SEA_BLOCKS);
        HeightSpan span = HeightSpan.of(cave, engine.getMinHeight(), surfaceY);
        long seed = CaveCarver.seedOf(engine.getSeedManager().getCarve(), request.key());
        CaveCarver carver = new CaveCarver(cave, new CngNoiseFactory(engine.getData()), seed, engine.getMinHeight(), engine.getHeight());
        int originX = Math.floorMod(request.variant(), VARIANTS) * VARIANT_SPACING_BLOCKS;
        CarvedSpace carved = new CarvedSpace(request.size(), span);
        carveSquare(carver, originX, surfaceY, carved);
        return carved.outside();
    }

    @Override
    public boolean tracesParts() {
        return true;
    }

    private static IrisCave caveOf(IrisData data, String key) {
        IrisCave cave = data.getCaveLoader().load(key);
        if (cave == null) {
            throw new NoSuchElementException("No cave " + key);
        }
        return cave;
    }

    private static void carveSquare(CaveCarver carver, int originX, int surfaceY, CarvedSpace carved) {
        int[] surface = new int[CHUNK_COLUMNS];
        Arrays.fill(surface, surfaceY);
        double[] listedEverywhere = new double[CHUNK_COLUMNS];
        Arrays.fill(listedEverywhere, 1D);
        boolean[] sealed = new boolean[CHUNK_COLUMNS];
        for (int chunkX = 0; chunkX < carved.size; chunkX += CHUNK_BLOCKS) {
            for (int chunkZ = 0; chunkZ < carved.size; chunkZ += CHUNK_BLOCKS) {
                int fromX = chunkX;
                int fromZ = chunkZ;
                carver.carveByPart(originX + chunkX, chunkZ, surface, listedEverywhere, sealed,
                        (part, localX, y, localZ) -> carved.mark(fromX + localX, y, fromZ + localZ, part));
            }
        }
    }

    /** The heights shown, from the dimension's bottom: the cave's own depth where it has one, below the ground. */
    private record HeightSpan(int bottomY, int topY) {
        static HeightSpan of(IrisCave cave, int worldBottomY, int surfaceY) {
            int topY = cave.depth().map(depth -> Math.min(surfaceY, (int) Math.ceil(depth.max().highest()) - worldBottomY)).orElse(surfaceY);
            int depthBottomY = cave.depth().map(depth -> (int) Math.floor(depth.min().lowest()) - worldBottomY).orElse(0);
            int bottomY = Math.max(0, Math.max(depthBottomY, topY - MAX_DEPTH_BLOCKS));
            return new HeightSpan(bottomY, Math.max(bottomY, topY));
        }

        int height() {
            return topY - bottomY + 1;
        }
    }

    /** The cells of the scene, each rock or carved by a part; a cell two parts carve keeps the last. */
    private static final class CarvedSpace {
        private final int size;
        private final HeightSpan span;
        private final byte[] cells;

        CarvedSpace(int size, HeightSpan span) {
            this.size = size;
            this.span = span;
            this.cells = new byte[size * size * span.height()];
        }

        void mark(int x, int worldY, int z, CavePart part) {
            int y = worldY - span.bottomY();
            if (y < 0 || y >= span.height()) {
                return;
            }
            cells[cellOf(x, y, z)] = (byte) (part.ordinal() + 1);
        }

        // Only the carved blocks on the outside of the space are kept, as the inside could not be seen.
        SceneBlocks outside() {
            SceneBlocks scene = new SceneBlocks();
            int height = span.height();
            for (int y = 0; y < height; y++) {
                String band = DEPTH_BANDS[Math.min(DEPTH_BANDS.length - 1, (height - 1 - y) * DEPTH_BANDS.length / height)];
                for (int z = 0; z < size; z++) {
                    for (int x = 0; x < size; x++) {
                        byte cell = cells[cellOf(x, y, z)];
                        if (cell != ROCK && touchesRock(x, y, z)) {
                            scene.add(x, y, z, band, cell - 1);
                        }
                    }
                }
            }
            return scene;
        }

        private boolean touchesRock(int x, int y, int z) {
            for (int[] step : NEIGHBOURS) {
                int nx = x + step[0];
                int ny = y + step[1];
                int nz = z + step[2];
                boolean inside = nx >= 0 && ny >= 0 && nz >= 0 && nx < size && ny < span.height() && nz < size;
                if (!inside || cells[cellOf(nx, ny, nz)] == ROCK) {
                    return true;
                }
            }
            return false;
        }

        private int cellOf(int x, int y, int z) {
            return (y * size + z) * size + x;
        }
    }
}
