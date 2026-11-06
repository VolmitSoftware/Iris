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

import com.volmit.tooling.Sender;

import java.util.Optional;

/**
 * Who sent a tooling command, as the adapter that received it knows them: the module-facing {@link Sender}, and what the
 * dispatcher needs to decide who may run a command and which pack it runs against. An adapter reads the platform state
 * here when it builds the sender, on the thread the platform needs, so the dispatcher may run anywhere.
 */
public interface ToolingSender extends Sender {
    /** Whether the platform treats the sender as an operator: the Bukkit permission {@code iris.all}, game master level on modded. */
    boolean isOperator();

    /** The Iris world the sender stands in; empty for the console or a world Iris does not generate. */
    Optional<SenderWorld> world();

    /** The pack of the studio the sender has open; empty when none is, and always on a platform without a studio. */
    Optional<ServedPack> openStudioPack();
}
