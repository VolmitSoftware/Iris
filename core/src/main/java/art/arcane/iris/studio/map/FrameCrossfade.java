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

import javax.swing.Timer;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the last frame of what the explorer showed before on screen while the next thing loads, then fades it out over
 * the first frame of the new one, so switching never shows an empty view in between.
 */
final class FrameCrossfade {
    private static final long FADE_NANOS = TimeUnit.MILLISECONDS.toNanos(70L);
    private static final int REPAINT_INTERVAL_MILLIS = 16;

    private final Timer repaintTimer;
    private Phase phase = Phase.NONE;
    private NoiseRenderCoordinator.Result outgoing;
    private long fadeStartedNanos;

    FrameCrossfade(Runnable repaint) {
        this.repaintTimer = new Timer(REPAINT_INTERVAL_MILLIS, event -> {
            outgoing();
            repaint.run();
        });
    }

    /**
     * Keeps showing the frame until the next one arrives. Without a frame, as when the switch before hasn't drawn yet,
     * the frame already held stays.
     */
    void hold(NoiseRenderCoordinator.Result frame) {
        if (frame == null && outgoing != null) {
            enter(Phase.HOLDING, outgoing);
            return;
        }
        enter(frame == null ? Phase.NONE : Phase.HOLDING, frame);
    }

    /**
     * The new frame is in, so the held one starts fading out over it.
     */
    void reveal() {
        if (phase != Phase.HOLDING) {
            return;
        }
        fadeStartedNanos = System.nanoTime();
        phase = Phase.FADING;
        repaintTimer.start();
    }

    void clear() {
        enter(Phase.NONE, null);
    }

    /**
     * @return the frame to draw over the current one, or null when there is none
     */
    NoiseRenderCoordinator.Result outgoing() {
        if (phase == Phase.FADING && fadedShare() >= 1F) {
            clear();
        }
        return outgoing;
    }

    float outgoingOpacity() {
        return phase == Phase.FADING ? 1F - fadedShare() : 1F;
    }

    private float fadedShare() {
        return Math.min(1F, (System.nanoTime() - fadeStartedNanos) / (float) FADE_NANOS);
    }

    private void enter(Phase nextPhase, NoiseRenderCoordinator.Result frame) {
        phase = nextPhase;
        outgoing = frame;
        if (nextPhase != Phase.FADING) {
            repaintTimer.stop();
        }
    }

    private enum Phase {
        NONE,
        HOLDING,
        FADING
    }
}
