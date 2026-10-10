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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.deposit.DepositVariant;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;

import java.util.HashMap;
import java.util.Map;

/**
 * One {@link DepositVariant} made ready: its source and replacement block ids resolved to the platform's blocks, built once on
 * first use. A rule whose source or replacement the platform does not know remaps nothing.
 */
final class ResolvedDepositVariant {
    private final DepositVariant settings;
    private final AtomicCache<Map<String, PlatformBlockState>> remapped = new AtomicCache<>();

    ResolvedDepositVariant(DepositVariant settings) {
        this.settings = settings;
    }

    boolean appliesAt(int worldY) {
        return worldY >= settings.minHeight() && worldY <= settings.maxHeight();
    }

    /** The block {@code ore} is rewritten to, or null when the rule does not remap its material. */
    PlatformBlockState remapOrNull(PlatformBlockState ore) {
        if (ore == null || settings.remap().isEmpty()) {
            return null;
        }
        return remapped.aquire(this::resolveRemap).get(IrisProceduralBlocks.materialKey(ore));
    }

    private Map<String, PlatformBlockState> resolveRemap() {
        Map<String, PlatformBlockState> resolved = new HashMap<>();
        for (Map.Entry<String, String> entry : settings.remap().entrySet()) {
            PlatformBlockState source = B.getStateOrNull(entry.getKey(), false);
            PlatformBlockState target = B.getStateOrNull(entry.getValue(), true);
            if (source == null || target == null) {
                continue;
            }
            resolved.put(IrisProceduralBlocks.materialKey(source), target);
        }
        return resolved;
    }
}
