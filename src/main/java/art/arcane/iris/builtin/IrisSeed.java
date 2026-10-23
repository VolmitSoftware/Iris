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

package art.arcane.iris.builtin;

import com.volmit.world.PackWorld;

/**
 * The seeds Iris derives from a world's seed for its own features. Iris derives them one after another from a shared
 * random, so they cannot be worked out from the world seed alone. Built-in modules seed their randoms and noise with
 * them, so worlds generated before the features were modules generate the same; a pack module derives its own seeds
 * from {@link PackWorld#seed()}. A world Iris generates answers for them through its {@link IrisSeeds} extension.
 */
public enum IrisSeed {
    BIOME, CARVE, COMPLEX, COMPONENT, DECORATOR, DEPOSIT, ENTITY, LOOT, MANTLE, POST, TERRAIN
}
