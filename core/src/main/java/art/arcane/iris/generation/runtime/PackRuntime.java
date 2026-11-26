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

import art.arcane.iris.pack.loading.IrisData;

import java.util.Objects;

/**
 * The runtime objects one owner derives from pack objects: seeded generators, samplers and profiles. Pack objects are
 * read-only configuration, so everything built for a seed, an engine or a world lives here and dies with the owner.
 * An engine owns one per generation runtime; engine-less tools use the scope their {@link IrisData} owns.
 */
public final class PackRuntime {
    private final Engine engine;
    private final IrisData data;

    private PackRuntime(Engine engine, IrisData data) {
        this.engine = engine;
        this.data = Objects.requireNonNull(data, "pack data");
    }

    static PackRuntime forEngine(Engine engine, IrisData data) {
        return new PackRuntime(Objects.requireNonNull(engine, "engine"), data);
    }

    public static PackRuntime standalone(IrisData data) {
        return new PackRuntime(null, data);
    }

    /**
     * The runtime scope for work on this pack: the engine's while one is generating, otherwise the scope the pack
     * data owns. Resolve it once per chunk or column and pass it down.
     */
    public static PackRuntime of(Engine engine, IrisData data) {
        PackRuntime engineRuntime = engine == null ? null : engine.getPackRuntime();
        if (engineRuntime != null) {
            return engineRuntime;
        }
        return Objects.requireNonNull(data, "pack data").standalonePackRuntime();
    }

    /** The owning engine, or null for an engine-less scope. */
    public Engine engine() {
        return engine;
    }

    public IrisData data() {
        return data;
    }
}
