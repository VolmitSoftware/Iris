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

import art.arcane.iris.generation.runtime.EngineRuntimeBuilder.RuntimeAssembly;
import art.arcane.iris.generation.runtime.IrisEngine.BiomeEnvironmentBinding;
import art.arcane.iris.generation.runtime.IrisEngine.GenerationRuntimeBinding;
import art.arcane.iris.generation.runtime.IrisEngine.GenerationRuntimeScope;

/**
 * Every per-thread engine binding (runtime assembly, biome environment, generation runtime scope) in one frame.
 * The engine getters run on generation hot paths, where a ThreadLocal lookup probes long collision chains in the
 * workers' crowded thread-local maps. Frames therefore live in thread slots the engine owns, so a closed engine
 * leaves nothing behind on its pooled workers.
 */
final class EngineThreadState {
    private final ThreadSlots<Frame> frames = ThreadSlots.bindings();

    Frame current() {
        return frames.get();
    }

    GenerationRuntimeBinding binding() {
        Frame frame = current();
        return frame == null ? null : frame.binding;
    }

    RuntimeAssembly assembly() {
        Frame frame = current();
        return frame == null ? null : frame.assembly;
    }

    void setAssembly(RuntimeAssembly assembly) {
        if (assembly != null) {
            frame().assembly = assembly;
            return;
        }
        Frame frame = current();
        if (frame != null) {
            frame.assembly = null;
            releaseIfEmpty(frame);
        }
    }

    BiomeEnvironmentBinding environment() {
        Frame frame = current();
        return frame == null ? null : frame.environment;
    }

    void setEnvironment(BiomeEnvironmentBinding environment) {
        if (environment != null) {
            frame().environment = environment;
            return;
        }
        Frame frame = current();
        if (frame != null) {
            frame.environment = null;
            releaseIfEmpty(frame);
        }
    }

    GenerationRuntimeScope open(GenerationRuntimeBinding binding) {
        Thread owner = Thread.currentThread();
        Frame frame = frame();
        GenerationRuntimeBinding previous = frame.binding;
        frame.binding = binding;
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
        Frame frame = current();
        if ((frame == null ? null : frame.binding) != installed) {
            throw new IllegalStateException("Iris generation runtime scopes must close in LIFO order.");
        }
        if (previous != null) {
            frame().binding = previous;
            return;
        }
        if (frame != null) {
            frame.binding = null;
            releaseIfEmpty(frame);
        }
    }

    private Frame frame() {
        Frame frame = current();
        if (frame != null) {
            return frame;
        }
        frame = new Frame();
        frames.set(frame);
        return frame;
    }

    private void releaseIfEmpty(Frame frame) {
        if (frame.assembly != null || frame.environment != null || frame.binding != null) {
            return;
        }
        frames.remove();
    }

    static final class Frame {
        RuntimeAssembly assembly;
        BiomeEnvironmentBinding environment;
        GenerationRuntimeBinding binding;
    }
}
