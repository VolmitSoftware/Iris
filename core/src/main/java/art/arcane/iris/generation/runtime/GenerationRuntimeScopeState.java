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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.runtime.IrisEngine.GenerationRuntimeBinding;
import art.arcane.iris.generation.runtime.IrisEngine.GenerationRuntimeScope;

final class GenerationRuntimeScopeState {
    private final ThreadLocal<GenerationRuntimeBinding> current = new ThreadLocal<>();

    GenerationRuntimeBinding current() {
        return current.get();
    }

    GenerationRuntimeScope open(GenerationRuntimeBinding binding) {
        Thread owner = Thread.currentThread();
        GenerationRuntimeBinding previous = current.get();
        current.set(binding);
        return new GenerationRuntimeScope(this, owner, previous, binding);
    }

    void close(
            Thread owner,
            GenerationRuntimeBinding previous,
            GenerationRuntimeBinding installed
    ) {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Iris generation runtime scope closed from a different thread.");
        }
        if (current.get() != installed) {
            throw new IllegalStateException("Iris generation runtime scopes must close in LIFO order.");
        }
        if (previous == null) {
            current.remove();
            return;
        }
        current.set(previous);
    }
}
