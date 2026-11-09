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

import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.VolmitSender;
import com.volmit.tooling.Sender;
import com.volmit.tooling.world.ToolPlayer;

import java.util.Optional;

/**
 * An Iris {@link VolmitSender}, such as the console, as a tool {@link Sender}, for core code that runs tools without a
 * command. It is never a player, because only a command's sender knows its tool player.
 */
public final class VolmitToolSender implements Sender {
    private final VolmitSender messages;

    public VolmitToolSender(VolmitSender messages) {
        this.messages = messages;
    }

    @Override
    public String name() {
        return messages.getS().getName();
    }

    @Override
    public void info(String message) {
        messages.sendMessage(C.WHITE + message);
    }

    @Override
    public void success(String message) {
        messages.sendMessage(C.GREEN + message);
    }

    @Override
    public void error(String message) {
        messages.sendMessage(C.RED + message);
    }

    @Override
    public <T> Optional<T> extension(Class<T> type) {
        return type.isInstance(messages.getS()) ? Optional.of(type.cast(messages.getS())) : Optional.empty();
    }

    @Override
    public Optional<ToolPlayer> player() {
        return Optional.empty();
    }
}
