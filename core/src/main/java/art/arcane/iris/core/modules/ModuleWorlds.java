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

package art.arcane.iris.core.modules;

import art.arcane.iris.engine.framework.Engine;
import com.volmit.host.LifecycleRunner;

/**
 * The worlds modules see for one engine runtime, and their lifecycle ({@link com.volmit.ModuleLifecycle}). The runtime
 * starts its world once it is published, and closes it when it retires: on a hotload, which builds a new runtime with a
 * new world, and when the engine closes. When the pack's resources reload while the runtime runs, such as after a tool
 * rewrote structure files, the world restarts in place: modules stop the old world, load the new snapshot and start a
 * new world with empty state.
 * <p>
 * An invocation keeps the world it first asked for. Iris does not wait for running invocations on an in-place restart,
 * so it closes the old world's state only at the next restart or when the runtime closes, and an invocation that began
 * before the restart can finish with it.
 * When the pack's modules fail to load, worlds run without hooks: the failure is reported where the modules load, and
 * generation reports it again.
 */
public final class ModuleWorlds {
    private enum Phase { NOT_STARTED, STARTED, CLOSED }

    private final Engine engine;
    private volatile IrisPackWorld current;
    private IrisPackWorld retired;
    private LifecycleRunner startedWith;
    private Phase phase = Phase.NOT_STARTED;

    public ModuleWorlds(Engine engine) {
        this.engine = engine;
    }

    /** The world the runtime generates now, made the first time anyone asks. */
    public IrisPackWorld current() {
        IrisPackWorld world = current;
        if (world != null) {
            return world;
        }
        synchronized (this) {
            if (current == null) {
                current = new IrisPackWorld(engine);
            }
            return current;
        }
    }

    /** Starts the world: tells modules about the pack snapshot if no world started with it yet, then starts the world. */
    public synchronized void start() {
        if (phase != Phase.NOT_STARTED) {
            throw new IllegalStateException("The modules of this Iris runtime were started already");
        }
        if (current().isStale()) {
            replaceUnstartedWorld();
        }
        startCurrentWorld();
        phase = Phase.STARTED;
    }

    /**
     * The pack's resources were reloaded under the running runtime: stops the world and starts a new one with the new
     * snapshot. A runtime that has not started yet only swaps its world, which then starts with the new snapshot.
     */
    public synchronized void restart() {
        switch (phase) {
            case NOT_STARTED -> replaceUnstartedWorld();
            case STARTED -> restartStartedWorld();
            case CLOSED -> {
            }
        }
    }

    /** Stops the world, if it started, and closes the state of the current and the previously retired world. */
    public synchronized void close() {
        if (phase == Phase.CLOSED) {
            return;
        }
        phase = Phase.CLOSED;
        RuntimeException failure = attempt(this::stopCurrentWorld, null);
        failure = attempt(this::closeRetiredWorld, failure);
        failure = attempt(this::closeCurrentWorld, failure);
        throwIfFailed(failure);
    }

    /** Stops the world and starts a new one even when stopping fails, so the runtime never keeps generating unstarted. */
    private void restartStartedWorld() {
        RuntimeException failure = attempt(this::stopCurrentWorld, null);
        failure = attempt(this::closeRetiredWorld, failure);
        retired = current;
        current = null;
        failure = attempt(this::startCurrentWorld, failure);
        throwIfFailed(failure);
    }

    /** Drops the world nothing started yet, if one was made, so the next one is made with the current snapshot. */
    private void replaceUnstartedWorld() {
        IrisPackWorld unstarted = current;
        current = null;
        if (unstarted != null) {
            unstarted.closeStates();
        }
    }

    private void closeCurrentWorld() {
        if (current != null) {
            current.closeStates();
        }
    }

    private void startCurrentWorld() {
        LifecycleRunner lifecycles = lifecyclesOfPack();
        if (lifecycles == null) {
            return;
        }
        IrisPackWorld world = current();
        world.resources().loadOnce(() -> lifecycles.packLoaded(world.resources(), new PackDeclaredSettings(engine.getData())));
        lifecycles.worldStarted(world);
        startedWith = lifecycles;
    }

    private void stopCurrentWorld() {
        LifecycleRunner lifecycles = startedWith;
        startedWith = null;
        if (lifecycles != null) {
            lifecycles.worldStopped(current());
        }
    }

    private void closeRetiredWorld() {
        if (retired == null) {
            return;
        }
        IrisPackWorld closing = retired;
        retired = null;
        closing.closeStates();
    }

    /** Runs {@code step}, adding what it throws to {@code failure}, the first failure of a sequence that must not stop. */
    private static RuntimeException attempt(Runnable step, RuntimeException failure) {
        try {
            step.run();
            return failure;
        } catch (RuntimeException stepFailure) {
            if (failure == null) {
                return stepFailure;
            }
            failure.addSuppressed(stepFailure);
            return failure;
        }
    }

    private static void throwIfFailed(RuntimeException failure) {
        if (failure != null) {
            throw failure;
        }
    }

    /** The hooks of the modules the engine's pack loads, or null when they fail to load. */
    private LifecycleRunner lifecyclesOfPack() {
        try {
            return new LifecycleRunner(engine.getData().getModules().pack().catalog());
        } catch (PackModulesException failure) {
            return null;
        }
    }
}
