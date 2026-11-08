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

package art.arcane.iris.modded.command;

import art.arcane.iris.core.tooling.SenderWorld;
import art.arcane.iris.core.tooling.ServedPack;
import art.arcane.iris.core.tooling.ToolingSender;
import art.arcane.iris.engine.framework.Engine;
import com.volmit.tooling.world.ToolPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * A modded source of a tooling command. It reads the world of the source when it is built, on the server thread that
 * received the command, and delivers messages on the server thread. A modded platform has no studio and no tool
 * players yet: tool worlds are not supported there, so {@link #player()} is empty.
 */
final class ModdedToolingSender implements ToolingSender {
    private final CommandSourceStack source;
    private final Optional<SenderWorld> world;

    ModdedToolingSender(CommandSourceStack source) {
        this.source = source;
        this.world = Optional.ofNullable(IrisModdedCommands.engineFor(source.getLevel()))
                .filter(engine -> !engine.isClosed())
                .map(SenderWorld::of);
    }

    @Override
    public String name() {
        return source.getTextName();
    }

    @Override
    public void info(String message) {
        onServerThread(() -> source.sendSuccess(() -> Component.literal(message), false));
    }

    @Override
    public void success(String message) {
        onServerThread(() -> ModdedCommandFeedback.ok(source, message));
    }

    @Override
    public void error(String message) {
        onServerThread(() -> ModdedCommandFeedback.fail(source, message));
    }

    @Override
    public <T> Optional<T> extension(Class<T> type) {
        if (type.isInstance(source)) {
            return Optional.of(type.cast(source));
        }
        return Optional.ofNullable(source.getEntity()).filter(type::isInstance).map(type::cast);
    }

    @Override
    public Optional<ToolPlayer> player() {
        return Optional.empty();
    }

    @Override
    public boolean isOperator() {
        return ModdedCommandTree.GATE.test(source);
    }

    @Override
    public Optional<SenderWorld> world() {
        return world;
    }

    @Override
    public Optional<ServedPack> openStudioPack() {
        return Optional.empty();
    }

    private void onServerThread(Runnable delivery) {
        source.getServer().execute(delivery);
    }
}
