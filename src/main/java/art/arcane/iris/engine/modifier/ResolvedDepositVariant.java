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

import art.arcane.iris.modules.deposit.DepositVariant;
import art.arcane.volmlib.util.cache.AtomicCache;
import com.volmit.ModuleLog;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.resource.ResourceId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One {@link DepositVariant} made ready: its source and replacement block ids resolved to the platform's blocks, built once on
 * first use. A rule whose source or replacement the platform does not know remaps nothing.
 */
final class ResolvedDepositVariant {
    private final DepositVariant settings;
    private final BlockRegistry registry;
    private final ModuleLog log;
    private final AtomicCache<Map<ResourceId, BlockState>> remapped = new AtomicCache<>();

    ResolvedDepositVariant(DepositVariant settings, BlockRegistry registry, ModuleLog log) {
        this.settings = settings;
        this.registry = registry;
        this.log = log;
    }

    boolean appliesAt(int worldY) {
        return worldY >= settings.minHeight() && worldY <= settings.maxHeight();
    }

    /** The block {@code ore} is rewritten to, or null when the rule does not remap its material. */
    BlockState remapOrNull(BlockState ore) {
        if (ore == null || settings.remap().isEmpty()) {
            return null;
        }
        return remapped.aquire(this::resolveRemap).get(ore.material());
    }

    private Map<ResourceId, BlockState> resolveRemap() {
        Map<ResourceId, BlockState> resolved = new HashMap<>();
        for (Map.Entry<String, String> entry : settings.remap().entrySet()) {
            Optional<BlockState> source = known(entry.getKey(), false);
            Optional<BlockState> target = known(entry.getValue(), true);
            if (source.isEmpty() || target.isEmpty()) {
                continue;
            }
            resolved.put(source.get().material(), target.get());
        }
        return resolved;
    }

    /** The block the platform has under {@code key}; a replacement it lacks is reported once. */
    private Optional<BlockState> known(String key, boolean reportMissing) {
        Optional<BlockState> block = registry.find(key);
        if (block.isEmpty() && reportMissing) {
            log.warnOnce("deposit-variant-block:" + key, "Deposit variant replaces ores with " + key + ", which this server does not have");
        }
        return block;
    }
}
