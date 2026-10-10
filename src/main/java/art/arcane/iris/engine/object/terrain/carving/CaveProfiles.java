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

package art.arcane.iris.engine.object.terrain.carving;

import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.core.loader.IrisRegistrant;

/** The cave profiles dimensions, regions and biomes declare ({@code caveProfile}). */
public final class CaveProfiles {
    private CaveProfiles() {
    }

    /** The profile {@code owner}, a dimension, region or biome, declares; null when it declares none, which is as good as one that is not enabled. */
    public static CaveProfile declaredBy(IrisRegistrant owner) {
        return owner.getModuleSettings().find(CaveProfile.class).orElse(null);
    }
}
