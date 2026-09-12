/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.world.pregen;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class PregenPhaseTracker {
    private final AtomicReference<State> state = new AtomicReference<>(new State(false, false, false, false));

    public List<PregenApiPhase> onTick(boolean pausedNow) {
        while (true) {
            State current = state.get();
            if (current.finished()) {
                return List.of();
            }

            State next = new State(true, pausedNow, false, false);
            List<PregenApiPhase> phases;
            if (!current.started()) {
                phases = List.of(PregenApiPhase.STARTED, PregenApiPhase.TICK);
            } else if (pausedNow != current.paused()) {
                phases = List.of(pausedNow ? PregenApiPhase.PAUSED : PregenApiPhase.RESUMED, PregenApiPhase.TICK);
            } else {
                phases = List.of(PregenApiPhase.TICK);
            }

            if (state.compareAndSet(current, next)) {
                return phases;
            }
        }
    }

    public List<PregenApiPhase> onSaving() {
        while (true) {
            State current = state.get();
            if (current.finished() || current.saving()) {
                return List.of();
            }

            State next = new State(current.started(), current.paused(), true, false);
            if (state.compareAndSet(current, next)) {
                return List.of(PregenApiPhase.SAVING);
            }
        }
    }

    public List<PregenApiPhase> onClose(boolean completed) {
        while (true) {
            State current = state.get();
            if (current.finished()) {
                return List.of();
            }

            State next = new State(current.started(), current.paused(), current.saving(), true);
            if (state.compareAndSet(current, next)) {
                return List.of(completed ? PregenApiPhase.COMPLETED : PregenApiPhase.CANCELLED);
            }
        }
    }

    private record State(boolean started, boolean paused, boolean saving, boolean finished) {
    }
}
