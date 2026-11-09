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

package art.arcane.iris.core.runtime;

import art.arcane.iris.core.project.IrisProject;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A tool world that holds the studio slot, the way an open studio project does: opening another studio, the studio
 * close command and a server stop go through it. The studio service asks it, and the platform's tool world answers for
 * the tool that owns the world.
 */
public interface StudioSlotOccupant {
    /**
     * The studio project the world is, which the studio service shows to every command that asks for the open studio: it
     * is open while the world is and carries the world's generator, as the project of any other studio does.
     */
    IrisProject project();

    /** A message that vetoes closing the world now; empty lets it close. */
    Optional<String> closeRefusal();

    /**
     * Gets the world ready to be replaced by another studio, such as finishing a save. Completes when the world may be
     * closed, or fails with the message to show the user.
     *
     * @param requestedBy the player who asked for the replacement; empty when the console did
     */
    CompletableFuture<Void> prepareReplacement(Optional<UUID> requestedBy);

    /** Closes the world and sends its players back; completes when it is unloaded and deleted. */
    CompletableFuture<Void> close();
}
