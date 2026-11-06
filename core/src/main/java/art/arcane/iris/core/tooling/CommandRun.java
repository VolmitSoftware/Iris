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

import com.volmit.resource.PlatformRegistries;
import com.volmit.tooling.Argument;
import com.volmit.tooling.CommandContext;
import com.volmit.tooling.Sender;
import com.volmit.tooling.ToolPack;
import com.volmit.world.PackWorld;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/** One run of a command, or one completion: the arguments parsed so far, and what the command runs against. */
final class CommandRun implements CommandContext {
    private final Sender sender;
    private final ToolPack pack;
    private final Optional<PackWorld> world;
    private final Optional<PlatformRegistries> platform;
    private final Map<Argument<?>, Object> values = new IdentityHashMap<>();

    CommandRun(Sender sender, ToolPack pack, Optional<PackWorld> world, Optional<PlatformRegistries> platform) {
        this.sender = sender;
        this.pack = pack;
        this.world = world;
        this.platform = platform;
    }

    <T> void put(Argument<T> argument, T value) {
        values.put(argument, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Argument<T> argument) {
        if (values.containsKey(argument)) {
            return (T) values.get(argument);
        }
        if (argument.isOptional()) {
            return argument.fallback();
        }
        throw new IllegalStateException("The sender gave no value for " + argument.name() + " yet");
    }

    @Override
    public Sender sender() {
        return sender;
    }

    @Override
    public ToolPack pack() {
        return pack;
    }

    @Override
    public Optional<PackWorld> world() {
        return world;
    }

    @Override
    public Optional<PlatformRegistries> platform() {
        return platform;
    }
}
