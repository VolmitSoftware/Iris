/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.runtime;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Runtime objects of one kind keyed by the identity of the pack object they were built from. Pack types compare by
 * value, so two equal configurations still get their own runtime object. A builder may resolve other runtime objects
 * from the same table, so entries are built outside the map and the first one published wins.
 */
public final class RuntimeTable<C, R> {
    private final ConcurrentHashMap<Identity, R> entries = new ConcurrentHashMap<>();
    private final Function<C, R> builder;

    public RuntimeTable(Function<C, R> builder) {
        this.builder = Objects.requireNonNull(builder, "runtime builder");
    }

    public R get(C config) {
        Identity key = new Identity(Objects.requireNonNull(config, "pack object"));
        R existing = entries.get(key);
        if (existing != null) {
            return existing;
        }
        R built = Objects.requireNonNull(builder.apply(config), "runtime object");
        R raced = entries.putIfAbsent(key, built);
        return raced == null ? built : raced;
    }

    private static final class Identity {
        private final Object referent;
        private final int hash;

        private Identity(Object referent) {
            this.referent = referent;
            this.hash = System.identityHashCode(referent);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Identity identity && identity.referent == referent;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
