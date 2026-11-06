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

package art.arcane.iris.core.tooling;

import art.arcane.iris.engine.framework.Engine;
import com.volmit.world.PackWorld;

import java.util.Objects;

/** The Iris world a sender stands in, and the pack it generates with. */
public record SenderWorld(ServedPack pack, PackWorld world) {
    public SenderWorld {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(world, "world");
    }

    /** The world {@code engine} generates. */
    public static SenderWorld of(Engine engine) {
        return new SenderWorld(new IrisServedPack(engine.getData()), engine.getGeneration().world());
    }
}
