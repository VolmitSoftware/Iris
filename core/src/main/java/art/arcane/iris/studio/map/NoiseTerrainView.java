/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.studio.map;

import art.arcane.iris.spi.IrisLogging;

import java.awt.EventQueue;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * What the 3D noise view shows: endless terrain, sampled tile by tile wherever the camera looks. Whenever the camera
 * moves or another tile is sampled, what to draw is worked out again off the Swing thread.
 */
final class NoiseTerrainView {
    private final OrbitViewport viewport;
    private final ExecutorService renderWorkers;
    private final ExecutorService samplers;
    private final int workerCount;
    private final ExecutorService coverer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Iris Noise Terrain Cover");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean coverQueued = new AtomicBoolean();
    private volatile TerrainTiles tiles;
    private volatile Vec3 anchor;
    private volatile ViewState view;
    private volatile boolean hidden = true;
    private double centerX;
    private double centerZ;
    private boolean recentered = true;
    private TerrainCover.Result shown;
    private Runnable onProgress = () -> {
    };

    NoiseTerrainView(OrbitViewport viewport, ExecutorService renderWorkers, ExecutorService samplers, int workerCount) {
        this.viewport = viewport;
        this.renderWorkers = renderWorkers;
        this.samplers = samplers;
        this.workerCount = workerCount;
        viewport.onViewChange(this::viewChanged);
    }

    /**
     * Shows the ground around this point next, turning around it.
     */
    void centerOn(double worldX, double worldZ) {
        centerX = worldX;
        centerZ = worldZ;
        recentered = true;
        if (viewport.isShowingScene()) {
            viewport.centerPivot();
        }
    }

    /**
     * Samples the terrain anew, as the camera looks at it, keeping the camera if the view already shows some. The ground
     * shown so far stays until its tiles are sampled anew.
     */
    void show(TerrainSampling sampling) {
        if (recentered || anchor == null) {
            anchor = new Vec3(centerX, (sampling.floorY() + sampling.topY()) / 2D, centerZ);
            recentered = false;
        }
        TerrainTiles previous = tiles;
        Map<TerrainTile, TerrainLayer> shownBefore = previous == null ? Map.of() : previous.layersInUse();
        if (previous != null) {
            previous.close();
        }
        tiles = new TerrainTiles(sampling, samplers, workerCount, this::requestCover, shownBefore);
        hidden = false;
        viewChanged();
    }

    /**
     * Goes on sampling what the camera looks at after {@link #hide}, with the tiles sampled before.
     */
    void resume() {
        if (tiles == null) {
            return;
        }
        hidden = false;
        viewChanged();
    }

    // Nothing is sampled for a view no one sees.
    void hide() {
        hidden = true;
        TerrainTiles current = tiles;
        if (current != null) {
            current.sampleOnly(List.of(), Set.of());
        }
    }

    /**
     * @param listener runs on the Swing thread whenever the detail wanted or the tiles still to sample change
     */
    void onProgress(Runnable listener) {
        onProgress = listener;
    }

    // How many blocks wide the columns are that the view wants, or 0 before anything was worked out.
    int detailStep() {
        return shown == null ? 0 : shown.detailStep();
    }

    int pendingTiles() {
        return shown == null ? 0 : shown.pendingTiles();
    }

    /**
     * How the view looks at the ground for its newest camera, or null before it has any ground to look at.
     */
    GroundSight groundSight() {
        TerrainTiles current = tiles;
        ViewState state = view;
        Vec3 at = anchor;
        if (current == null || state == null || at == null || state.width() < 1 || state.height() < 1) {
            return null;
        }
        OrbitProjection projection = NoiseTerrainScene.projectionFrom(at, state.camera(), state.width(), state.height(), state.coveredLeftWidth());
        TerrainSampling sampling = current.sampling();
        int left = Math.max(0, Math.min(state.width() - 1, state.coveredLeftWidth()));
        return new GroundSight(projection, left, state.width(), state.height(), sampling.floorY(), sampling.topY());
    }

    void close() {
        coverer.shutdownNow();
        TerrainTiles current = tiles;
        if (current != null) {
            current.close();
        }
    }

    // Before the first scene, the viewport still holds whatever camera it had; the scene will start it afresh.
    private void viewChanged() {
        OrbitCamera camera = viewport.isShowingScene() ? viewport.camera() : NoiseTerrainScene.START;
        view = new ViewState(camera, viewport.getWidth(), viewport.getHeight(), viewport.coveredLeftWidth());
        requestCover();
    }

    // Requests while one waits to start are one and the same: it will see the newest view and tiles anyway.
    private void requestCover() {
        if (coverQueued.getAndSet(true)) {
            return;
        }
        coverer.execute(() -> {
            coverQueued.set(false);
            coverSafely();
        });
    }

    private void coverSafely() {
        try {
            cover();
        } catch (RuntimeException error) {
            IrisLogging.reportError(error);
        }
    }

    private void cover() {
        TerrainTiles current = tiles;
        ViewState state = view;
        Vec3 at = anchor;
        if (hidden || current == null || state == null || at == null || state.width() < 1 || state.height() < 1) {
            return;
        }
        OrbitProjection projection = NoiseTerrainScene.projectionFrom(at, state.camera(), state.width(), state.height(), state.coveredLeftWidth());
        TerrainCover.Result result = TerrainCover.of(current, projection, state.width(), state.height(), state.coveredLeftWidth());
        TerrainSampling sampling = current.sampling();
        NoiseTerrainScene scene = new NoiseTerrainScene(result.layers(), at, sampling.floorY(), sampling.topY(),
                sampling.waterLevel(), renderWorkers, workerCount);
        EventQueue.invokeLater(() -> showCovered(current, scene, result));
    }

    // Until any ground is sampled, the view keeps what it showed before, or its loading animation.
    private void showCovered(TerrainTiles coveredTiles, NoiseTerrainScene scene, TerrainCover.Result result) {
        if (coveredTiles != tiles) {
            return;
        }
        shown = result;
        onProgress.run();
        if (scene.isEmpty() && result.pendingTiles() > 0) {
            return;
        }
        if (viewport.isShowingScene()) {
            viewport.replaceScene(scene);
        } else {
            viewport.show(scene);
        }
    }

    private record ViewState(OrbitCamera camera, int width, int height, int coveredLeftWidth) {
    }
}
