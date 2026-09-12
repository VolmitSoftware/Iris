/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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
package art.arcane.iris.pack.validation;

import java.util.Locale;

/**
 * What the gate did about a key that is missing on the running Minecraft version.
 * <ul>
 * <li>{@code EXCLUDED}: a compat unit (registrant or object placement) composes the missing content and was removed from
 * every pool that could pick it.</li>
 * <li>{@code DROPPED}: a single entry or reference was removed from its container; the container keeps generating.</li>
 * <li>{@code SUBSTITUTED}: a declared fallback (dimension {@code blockFallbacks} or a block {@code backup}) replaced the
 * missing key; the content still generates.</li>
 * </ul>
 */
public enum CompatAction {
    EXCLUDED,
    DROPPED,
    SUBSTITUTED;

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
