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

import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

/**
 * The purposes Iris's built-in modules ask {@link PackWorld#seed(ResourceId)} for, one per feature.
 */
public final class IrisSeedPurposes {
    public static final ResourceId BIOME = purpose("biome");
    public static final ResourceId CARVE = purpose("carve");
    public static final ResourceId COMPLEX = purpose("complex");
    public static final ResourceId COMPONENT = purpose("component");
    public static final ResourceId DECORATOR = purpose("decorator");
    public static final ResourceId DEPOSIT = purpose("deposit");
    public static final ResourceId ENTITY = purpose("entity");
    public static final ResourceId LOOT = purpose("loot");
    public static final ResourceId MANTLE = purpose("mantle");
    /** The random each chunk's objects are placed with, before the chunk's coordinates are mixed in. */
    public static final ResourceId OBJECT_CHUNK = purpose("object-chunk");
    /** The noise that picks the count of a placement with a density style. */
    public static final ResourceId OBJECT_DENSITY = purpose("object-density");
    /** The noise that warps the columns of object placements with a surface warp. */
    public static final ResourceId OBJECT_WARP = purpose("object-warp");
    /** The candidate spots, ranks and chance noise of separated object placements. */
    public static final ResourceId OBJECT_SEPARATION = purpose("object-separation");
    public static final ResourceId POST = purpose("post");
    public static final ResourceId TERRAIN = purpose("terrain");

    private static final String NAMESPACE = "iris";
    private static final String PATH_PREFIX = "seed/";

    private IrisSeedPurposes() {
    }

    private static ResourceId purpose(String name) {
        return new ResourceId(NAMESPACE, PATH_PREFIX + name);
    }
}
