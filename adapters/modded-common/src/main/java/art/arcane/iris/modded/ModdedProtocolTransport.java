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

package art.arcane.iris.modded;

import art.arcane.iris.platform.protocol.IrisServerTransport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

public final class ModdedProtocolTransport implements IrisServerTransport {
    private final MinecraftServer server;
    private final ModdedProtocolChannel channel;

    public ModdedProtocolTransport(MinecraftServer server, ModdedProtocolChannel channel) {
        this.server = Objects.requireNonNull(server, "server");
        this.channel = Objects.requireNonNull(channel, "protocol channel");
    }

    @Override
    public void sendToClient(String sessionId, byte[] frame) {
        if (sessionId == null || frame == null) {
            return;
        }
        UUID playerId = parseUuid(sessionId);
        if (playerId == null) {
            return;
        }
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            return;
        }
        ModdedIrisPayload payload = new ModdedIrisPayload(frame);
        scheduler.global(() -> deliver(playerId, payload));
    }

    private void deliver(UUID playerId, ModdedIrisPayload payload) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || !channel.canReceive(player)) {
            return;
        }
        channel.send(player, payload);
    }

    private static UUID parseUuid(String sessionId) {
        try {
            return UUID.fromString(sessionId);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
