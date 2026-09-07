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

package art.arcane.iris.util.common.data;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;

public final class BoundBlockState {
    private final String key;
    private volatile Bound bound;

    private BoundBlockState(String key) {
        this.key = key;
    }

    public static BoundBlockState of(String key) {
        return new BoundBlockState(key);
    }

    public PlatformBlockState get() {
        Bound current = bound;
        IrisPlatform platform = IrisPlatforms.getOrNull();

        if (current != null && (platform == null || current.platform() == platform)) {
            return current.state();
        }

        IrisPlatform resolvedPlatform = IrisPlatforms.get();
        Bound resolved = new Bound(resolvedPlatform, B.getState(key));
        bound = resolved;
        return resolved.state();
    }

    private record Bound(IrisPlatform platform, PlatformBlockState state) {
    }
}
