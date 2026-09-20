/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code feature_placement} worldcheck gate for the imported-feature pass.
 *
 * <p>Vanilla does not throw when a feature writes into a chunk it is not allowed to touch; it logs
 * {@code Detected setBlock in a far chunk} and drops the write. Reading back the world cannot distinguish that
 * from a feature that legitimately placed nothing, so the gate watches the log instead. The second marker,
 * {@code Requested chunk unavailable during world generation}, does throw, and is recorded from the feature
 * pass directly.
 *
 * <p>Only armed under {@code -Diris.worldcheck}. Outside a gated run every entry point here is a single
 * boolean read.
 */
final class WorldCheckFeaturePlacement {
    private static final boolean ENABLED = Boolean.getBoolean("iris.worldcheck");
    private static final String FAR_CHUNK_MARKER = "Detected setBlock in a far chunk";
    private static final String UNAVAILABLE_CHUNK_MARKER = "Requested chunk unavailable during world generation";
    private static final List<String> MARKERS = List.of(FAR_CHUNK_MARKER, UNAVAILABLE_CHUNK_MARKER);
    private static final int REPORTED_SAMPLE_MAX = 5;

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean PASS_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean SKIP_REPORTED = new AtomicBoolean();
    private static final AtomicInteger VIOLATIONS = new AtomicInteger();

    private WorldCheckFeaturePlacement() {
    }

    /**
     * Arms the log watch. Called when the feature table is published, which is before any chunk decorates, so
     * the watch is installed for the very first pass rather than from the second chunk onwards. The pass and
     * failure recorders arm too, for a path that publishes a table without going through prepare. Idempotent
     * and cheap when the gate is off.
     */
    static void arm() {
        if (!ENABLED || !INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            ((Logger) LogManager.getRootLogger()).addFilter(new PlacementWatch());
        } catch (Throwable error) {
            WorldCheckPredicates.qaEvent("feature_placement", "all", false,
                    "skipped=log-watch-unavailable," + error.getClass().getSimpleName());
        }
    }

    /**
     * Emits the passing gate event once, after the first clean chunk. A later violation emits its own failing
     * event, so a run that fails after passing still reports the failure.
     */
    static void recordPlacementPass() {
        if (!ENABLED) {
            return;
        }
        arm();
        if (VIOLATIONS.get() == 0 && PASS_REPORTED.compareAndSet(false, true)) {
            WorldCheckPredicates.qaEvent("feature_placement", "all", true, "violations=0");
        }
    }

    /**
     * Emits the not-asserted event once, so a gated run can tell "importedFeatures was off, nothing to check"
     * apart from "checked and clean".
     */
    static void recordFeaturesOff() {
        if (!ENABLED) {
            return;
        }
        if (SKIP_REPORTED.compareAndSet(false, true)) {
            WorldCheckPredicates.qaEvent("feature_placement", "all", true, "skipped=importedFeatures-off");
        }
    }

    static void recordPlacementFailure(int chunkX, int chunkZ, Throwable error) {
        if (!ENABLED) {
            return;
        }
        arm();
        String message = messageChain(error);
        String detail = message.contains(UNAVAILABLE_CHUNK_MARKER)
                ? "unavailableChunk"
                : "placementFailure";
        report(detail + ",chunk=" + chunkX + "," + chunkZ + ",error="
                + error.getClass().getSimpleName());
    }

    private static void report(String detail) {
        int count = VIOLATIONS.incrementAndGet();
        if (count <= REPORTED_SAMPLE_MAX) {
            WorldCheckPredicates.qaEvent("feature_placement", "all", false, detail + ",violations=" + count);
        }
    }

    private static String messageChain(Throwable error) {
        StringBuilder chain = new StringBuilder();
        Throwable current = error;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current.getMessage() != null) {
                chain.append(current.getMessage()).append('\n');
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return chain.toString();
    }

    private static final class PlacementWatch extends AbstractFilter {
        private PlacementWatch() {
            super(Filter.Result.NEUTRAL, Filter.Result.NEUTRAL);
        }

        @Override
        public Filter.Result filter(LogEvent event) {
            if (event == null || event.getMessage() == null) {
                return Filter.Result.NEUTRAL;
            }
            String message = event.getMessage().getFormattedMessage();
            if (message == null) {
                return Filter.Result.NEUTRAL;
            }
            for (String marker : MARKERS) {
                if (message.contains(marker)) {
                    report("logMarker=" + marker);
                    break;
                }
            }
            return Filter.Result.NEUTRAL;
        }
    }
}
