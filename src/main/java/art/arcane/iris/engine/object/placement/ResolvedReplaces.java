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
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import com.volmit.block.BlockRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The {@link ResolvedReplace}s of one engine runtime, one per decoded {@link ObjectReplace}, built when a placement first uses it. */
final class ResolvedReplaces {
    private final BlockRegistry blocks;
    private final Map<ObjectReplace, ResolvedReplace> resolved = new ConcurrentHashMap<>();

    ResolvedReplaces(Engine engine) {
        this(engine.getData().getPackResources().blocks());
    }

    ResolvedReplaces(BlockRegistry blocks) {
        this.blocks = blocks;
    }

    /** The replacements of the engine {@code data} serves, or of {@code data} alone when no engine runs it. */
    static ResolvedReplaces of(IrisData data) {
        Engine engine = data.getEngine();
        if (engine == null) {
            return new ResolvedReplaces(data.getPackResources().blocks());
        }
        return engine.getGeneration().state(ResolvedReplaces.class, ResolvedReplaces::new);
    }

    ResolvedReplace of(ObjectReplace settings) {
        return resolved.computeIfAbsent(settings, replace -> ResolvedReplace.of(replace, blocks));
    }
}
