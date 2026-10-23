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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.blocks.ObjectReplace;
import com.volmit.block.BlockRegistry;
import com.volmit.stage.PlacementStageContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The {@link ResolvedReplace}s of one pack's block registry, one per decoded {@link ObjectReplace}, built when a placement first uses it. */
final class ResolvedReplaces {
    private final BlockRegistry blocks;
    private final Map<ObjectReplace, ResolvedReplace> resolved = new ConcurrentHashMap<>();

    ResolvedReplaces(BlockRegistry blocks) {
        this.blocks = blocks;
    }

    /** The replacements of the world {@code context} places in. */
    static ResolvedReplaces of(PlacementStageContext context) {
        return context.world().state(ResolvedReplaces.class, world -> new ResolvedReplaces(world.resources().blocks()));
    }

    ResolvedReplace of(ObjectReplace settings) {
        return resolved.computeIfAbsent(settings, replace -> ResolvedReplace.of(replace, blocks));
    }
}
