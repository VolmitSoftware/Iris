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

package art.arcane.iris.generation.chunk;

import art.arcane.volmlib.util.hunk.Hunk;

public final class ColumnExtentListeningHunk<T> implements Hunk<T>, ColumnExtent {
    private final Hunk<T> source;
    private final Listener<T> listener;

    public ColumnExtentListeningHunk(Hunk<T> source, Listener<T> listener) {
        this.source = source;
        this.listener = listener;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        listener.onWrite(x, y, z, t);
        source.setRaw(x, y, z, t);
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return source.getRaw(x, y, z);
    }

    @Override
    public int getWidth() {
        return source.getWidth();
    }

    @Override
    public int getHeight() {
        return source.getHeight();
    }

    @Override
    public int getDepth() {
        return source.getDepth();
    }

    @Override
    public int highestStoredY(int x, int z) {
        return ColumnExtent.highestStoredY(source, x, z);
    }

    @FunctionalInterface
    public interface Listener<T> {
        void onWrite(int x, int y, int z, T value);
    }
}
