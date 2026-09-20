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

package art.arcane.iris.modded.api;

/**
 * The kinds of custom content a {@link ModdedDataProvider} can claim.
 * <p>
 * Constants may be added. Switch expressions over this enum need a {@code default} arm.
 */
public enum ModdedDataType {
    /** Block states, resolved through {@link ModdedDataProvider#getBlockData(String, java.util.Map)}. */
    BLOCK,
    /** Item types, claimed for loot and pack tooling. */
    ITEM,
    /** Entity types, spawned through {@link ModdedDataProvider#spawnMob(art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntityRuntime.CustomSpawn)}. */
    ENTITY
}
