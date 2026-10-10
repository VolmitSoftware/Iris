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

package art.arcane.iris.engine.spawn;

import art.arcane.volmlib.util.collection.KList;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Applies rarity to a pick: each possibility appears in the expansion as often as its rarity says, so picking one at
 * random from the expansion picks the rarer ones less often. Spawner choices go through this once; rarity must never be
 * applied a second time per spawn position.
 */
final class RarityWeighting {
    private static final int MINIMUM_RARITY = 1;

    private RarityWeighting() {
    }

    /** Each of {@code possibilities} repeated total/rarity times, where a rarity below 1 counts as 1. */
    static <T> KList<T> expand(List<T> possibilities, ToIntFunction<T> rarity) {
        KList<T> expanded = new KList<>();
        int totalRarity = 0;
        for (T possibility : possibilities) {
            totalRarity += rarityOf(possibility, rarity);
        }
        for (T possibility : possibilities) {
            expanded.addMultiple(possibility, totalRarity / rarityOf(possibility, rarity));
        }
        return expanded;
    }

    private static <T> int rarityOf(T possibility, ToIntFunction<T> rarity) {
        return Math.max(MINIMUM_RARITY, rarity.applyAsInt(possibility));
    }
}
