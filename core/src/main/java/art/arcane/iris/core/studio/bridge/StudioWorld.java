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

package art.arcane.iris.core.studio.bridge;

import art.arcane.iris.core.gui.GuiHost;
import art.arcane.iris.core.gui.GuiOverlay;
import art.arcane.iris.core.studio.map.MapTileRenderer;
import art.arcane.iris.engine.framework.Engine;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The world a bridge shows. Its engine is asked for again whenever the one in use closes, as a hotload or a restart of
 * the world does, so a bridge outlives them.
 */
final class StudioWorld {
    private final String name;
    private final Supplier<Engine> engines;
    private final UUID openerId;
    private Session session;

    /**
     * @param engines  the world's engine now; null while it has none
     * @param openerId the player the studio teleports, or null when the console opened it
     */
    StudioWorld(String name, Supplier<Engine> engines, UUID openerId) {
        this.name = Objects.requireNonNull(name, "name");
        this.engines = Objects.requireNonNull(engines, "engines");
        this.openerId = openerId;
    }

    String name() {
        return name;
    }

    /** The world's engine and what the bridge draws it with; null while the world has no running engine. */
    synchronized Session session() {
        if (session != null && isRunning(session.engine())) {
            return session;
        }
        Engine engine = engines.get();
        session = isRunning(engine)
                ? new Session(engine, new MapTileRenderer(engine), GuiHost.get().overlayFor(engine, openerId))
                : null;
        return session;
    }

    private static boolean isRunning(Engine engine) {
        return engine != null && !engine.isClosed() && !engine.isClosing() && engine.getComplex() != null;
    }

    /** @param overlay the server's players and teleports; null on a platform without one */
    record Session(Engine engine, MapTileRenderer renderer, GuiOverlay overlay) {
    }
}
