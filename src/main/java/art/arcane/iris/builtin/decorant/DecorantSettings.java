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

package art.arcane.iris.builtin.decorant;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

import java.util.Collections;
import java.util.List;

/**
 * The decorators one biome declares, written as {@code "decorators": [...]}, the list of decorators. IrisApi types only, like
 * {@link Decorator}.
 */
@Desc("Decorators are used for things like tall grass, bisected flowers, and even kelp or cactus (random heights)")
@Feature(id = "iris:decorant", name = "Decorators", key = "decorators", root = "decorators", targets = Level.BIOME)
public final class DecorantSettings implements IrisFeature {
    @Desc("Decorators are used for things like tall grass, bisected flowers, and even kelp or cactus (random heights)")
    private List<Decorator> decorators = List.of();

    public DecorantSettings() {
    }

    public DecorantSettings(List<Decorator> decorators) {
        this.decorators = List.copyOf(decorators);
    }

    public List<Decorator> decorators() {
        return decorators == null ? List.of() : Collections.unmodifiableList(decorators);
    }
}
