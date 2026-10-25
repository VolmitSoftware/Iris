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

package art.arcane.iris.studio.view;

import java.util.List;
import java.util.function.Consumer;

/**
 * What a desktop window may ask of the live server world. Called from the AWT event thread, so every
 * method hands work to the server's own threads and never blocks.
 */
public interface GuiOverlay {
    List<GuiMarker> players();

    void requestEntities(Consumer<List<GuiMarker>> sink);

    void teleport(double worldX, double worldZ);

    /**
     * Hands the opener's position to the sink, from a server thread. Platforms that can't tell which
     * player opened the window report them as offline.
     */
    default void requestOpenerPosition(Consumer<OpenerPosition> sink) {
        sink.accept(OpenerPosition.OFFLINE);
    }
}
