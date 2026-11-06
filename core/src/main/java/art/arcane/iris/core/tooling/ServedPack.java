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

package art.arcane.iris.core.tooling;

import com.volmit.ModuleLog;
import com.volmit.host.CommandRegistration;
import com.volmit.tooling.ToolPack;

import java.util.List;

/**
 * A pack the dispatcher runs module commands against. Two instances of one pack are equal, so the dispatcher can tell that
 * the pack a line names is the pack of the sender's world.
 */
public interface ServedPack {
    /** The pack's folder name, as {@code pack=<name>} writes it. */
    String name();

    /**
     * The root commands the pack's modules registered.
     *
     * @throws IllegalStateException when the pack's modules did not load, with the reason
     */
    List<CommandRegistration> commands();

    /** The pack as the commands of the module that owns {@code log} see it. */
    ToolPack toolPack(ModuleLog log);
}
