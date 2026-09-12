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
 * Registry a pack key is checked against by the {@link ContentGate}. Sounds and particles are absent on purpose: the
 * platform SPI exposes no registry for them and an unknown effect already plays nothing.
 */
public enum CompatRegistry {
    BLOCK,
    ITEM,
    ENTITY,
    BIOME,
    STRUCTURE,
    ENCHANTMENT,
    POTION_EFFECT;

    public String label() {
        return name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
