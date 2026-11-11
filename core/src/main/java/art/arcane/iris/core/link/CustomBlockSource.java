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

package art.arcane.iris.core.link;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What the studio needs to know of the blocks plugins add, without reaching for the server's plugin API.
 */
public interface CustomBlockSource {
    /**
     * Plugins save their blocks to region files as the vanilla state players see, and keep which block it really is in
     * files of their own. This is the way back from such a state.
     *
     * @return one look for every vanilla state some plugin block is saved as
     */
    List<CustomBlockLook> blocks();

    /**
     * Some plugins generate their blocks as states only the server knows, such as {@code craftengine:custom_7}, and
     * send players a vanilla state in their place.
     *
     * @param state a block state the server holds
     * @return the plugin block it is with the state players see, or empty when it is no plugin's block
     */
    Optional<CustomBlockLook> lookOf(String state);

    /**
     * @return the resource packs holding the models of the plugins' blocks, those that have one on disk
     */
    List<Path> resourcePacks();
}
