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
 * The purposes Iris's built-in modules ask {@link PackWorld#seed(ResourceId)} for. Iris answers each with the seed its
 * worlds always used for that feature, derived one after another from a shared random rather than by the API's rule,
 * so worlds generated before the features were modules generate the same.
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
