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

package art.arcane.iris.builtin.spawn;

import java.util.Objects;
import java.util.Optional;

/**
 * Iris extension of an {@link com.volmit.entity.EntitySpec}, read by Iris's Bukkit host with
 * {@code spec.extension(IrisEntityExtras.class)}: what Iris packs set on an entity that only Bukkit has a name for or
 * only Iris does. Other hosts ignore it.
 *
 * @param reason                     Bukkit's spawn reason by name, such as {@code COMMAND}; empty for a natural spawn
 * @param riseOutOfGround            whether the entity slowly climbs out of the ground when players are near
 * @param pandaMainGene              for pandas the main gene by Bukkit's name; empty to leave it to the platform
 * @param pandaHiddenGene            for pandas the hidden gene by Bukkit's name; empty to leave it to the platform
 * @param applySettingsToProvidedMob whether the rest of the spec also applies to a mob of a content provider, which is
 *                                   otherwise left as the provider set it up
 */
public record IrisEntityExtras(Optional<String> reason, boolean riseOutOfGround, Optional<String> pandaMainGene,
                               Optional<String> pandaHiddenGene, boolean applySettingsToProvidedMob) {
    /** What an entity without the extension gets: a natural spawn, standing, with the platform's genes. */
    public static final IrisEntityExtras NONE = new IrisEntityExtras(Optional.empty(), false, Optional.empty(), Optional.empty(), false);

    public IrisEntityExtras {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(pandaMainGene, "pandaMainGene");
        Objects.requireNonNull(pandaHiddenGene, "pandaHiddenGene");
    }
}
