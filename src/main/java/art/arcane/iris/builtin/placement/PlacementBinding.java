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

package art.arcane.iris.builtin.placement;

import java.util.function.Supplier;

/** The placement bound to the placing thread. */
final class PlacementBinding {
    private static final ScopedValue<ObjectPlacement> CURRENT = ScopedValue.newInstance();

    private PlacementBinding() {
    }

    static ObjectPlacement current() {
        if (!CURRENT.isBound()) {
            throw new IllegalStateException("No Iris object placement is running on this thread");
        }
        return CURRENT.get();
    }

    static <T> T run(ObjectPlacement placement, Supplier<T> body) {
        return ScopedValue.where(CURRENT, placement).call(body::get);
    }
}
