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

package art.arcane.iris.studio.bridge;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Opens the studio on another pack, on a platform that can replace the open studio world. */
public interface StudioPackSwitch {
    /** The packs the studio can open, in the order to list them. */
    List<StudioPack> packs();

    /**
     * Replaces the open studio world with one of {@code pack} and opens a bridge to the new world.
     *
     * @param openerId the player the old studio was opened for, who moves to the new one; null for the console
     */
    CompletableFuture<StudioBridge> open(String pack, UUID openerId);
}
