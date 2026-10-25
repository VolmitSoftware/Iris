package art.arcane.iris.studio.map;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TerrainCoverTest {
    private static final int WIDTH = 1200;
    private static final int HEIGHT = 800;
    private static final TerrainSampling FLAT = new TerrainSampling((grid, row, heights, colors) -> {
    }, 0, 64, Double.NaN);
    private static final OrbitProjection PROJECTION = NoiseTerrainScene.projectionFrom(Vec3.ZERO, NoiseTerrainScene.START,
            WIDTH, HEIGHT, 0);

    @Test
    public void backdropNotSampledYetBorrowsTheNearestSampledCoarserTile() {
        TerrainTile coarser = TerrainTile.containing(0D, 0D, backdropLevel() + 1);

        List<TerrainTile> drawn = drawnTiles(Map.of(coarser, flatLayer(coarser)));

        assertTrue(drawn.contains(coarser));
    }

    @Test
    public void tilesTooCoarseToCutHolesInDoNotStandIn() {
        TerrainTile tooCoarse = TerrainTile.containing(0D, 0D, backdropLevel() + TerrainHole.MOST_LEVELS_FINER + 1);

        List<TerrainTile> drawn = drawnTiles(Map.of(tooCoarse, flatLayer(tooCoarse)));

        assertFalse(drawn.contains(tooCoarse));
    }

    private static int backdropLevel() {
        TerrainCover.Result nothingSampled = TerrainCover.of(tilesWith(Map.of()), PROJECTION, WIDTH, HEIGHT, 0);
        int detailLevel = Integer.numberOfTrailingZeros(nothingSampled.detailStep());
        return detailLevel + 3;
    }

    private static List<TerrainTile> drawnTiles(Map<TerrainTile, TerrainLayer> sampled) {
        return TerrainCover.of(tilesWith(sampled), PROJECTION, WIDTH, HEIGHT, 0).layers().stream()
                .map(HoledLayer::tile)
                .toList();
    }

    private static TerrainTiles tilesWith(Map<TerrainTile, TerrainLayer> sampled) {
        return new TerrainTiles(FLAT, new NeverRuns(), 1, () -> {
        }, sampled);
    }

    private static TerrainLayer flatLayer(TerrainTile tile) {
        return TerrainLayer.of(NoiseTerrain.empty(tile.grid()), FLAT.floorY());
    }

    // Keeps the wanted tiles unsampled, so each test decides exactly what is ready.
    private static final class NeverRuns extends AbstractExecutorService {
        @Override
        public void execute(Runnable command) {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
