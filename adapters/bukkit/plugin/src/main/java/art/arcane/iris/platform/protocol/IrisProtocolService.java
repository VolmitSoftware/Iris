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

package art.arcane.iris.platform.protocol;

import art.arcane.iris.Iris;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.protocol.IrisProtocol;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.UUID;

public class IrisProtocolService implements IrisService, PluginMessageListener, IrisServerTransport {
    private static final long SERVER_CAPABILITIES = IrisProtocol.CAPABILITY_PREGEN
            | IrisProtocol.CAPABILITY_VISION
            | IrisProtocol.CAPABILITY_CURSOR
            | IrisProtocol.CAPABILITY_STUDIO;
    private static final int DIMENSION_SYNC_RETRY_TICKS = 20;
    private static final int DIMENSION_SYNC_MAX_ATTEMPTS = 15;

    private IrisSessionRegistry registry;
    private IrisProtocolServer protocolServer;
    private IrisCursorRequestService cursorService;
    private IrisVisionRequestService visionService;

    @Override
    public void onEnable() {
        Messenger messenger = Bukkit.getMessenger();
        messenger.registerOutgoingPluginChannel(Iris.instance, IrisProtocol.CHANNEL);
        messenger.registerIncomingPluginChannel(Iris.instance, IrisProtocol.CHANNEL, this);
        registry = new IrisSessionRegistry();
        protocolServer = new IrisProtocolServer(registry, SERVER_CAPABILITIES, brand(), true);
        EngineResolver engineResolver = IrisProtocolService::resolveEngine;
        protocolServer.setEngineResolver(engineResolver);
        cursorService = IrisCursorRequestService.create(engineResolver, registry);
        protocolServer.setCursorInfoHandler(cursorService);
        visionService = IrisVisionRequestService.create(engineResolver, registry);
        protocolServer.setVisionTileHandler(visionService);
        IrisServices.register(IrisProtocolServer.class, protocolServer);
        for (Player player : Bukkit.getOnlinePlayers()) {
            registry.register(new IrisSession(player.getUniqueId().toString(), this));
        }
    }

    @Override
    public void onDisable() {
        IrisServices.remove(IrisProtocolServer.class);
        Messenger messenger = Bukkit.getMessenger();
        messenger.unregisterIncomingPluginChannel(Iris.instance, IrisProtocol.CHANNEL, this);
        messenger.unregisterOutgoingPluginChannel(Iris.instance, IrisProtocol.CHANNEL);
        IrisSessionRegistry current = registry;
        IrisCursorRequestService cursor = cursorService;
        IrisVisionRequestService vision = visionService;
        if (current != null) {
            for (IrisSession session : current.all()) {
                current.unregister(session.id());
                if (cursor != null) {
                    cursor.clearSession(session.id());
                }
                if (vision != null) {
                    vision.clearSession(session.id());
                }
            }
        }
        registry = null;
        protocolServer = null;
        cursorService = null;
        visionService = null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        IrisSessionRegistry current = registry;
        if (current == null) {
            return;
        }
        current.register(new IrisSession(event.getPlayer().getUniqueId().toString(), this));
        syncDimension(event.getPlayer(), DIMENSION_SYNC_MAX_ATTEMPTS);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (registry == null) {
            return;
        }
        syncDimension(event.getPlayer(), 1);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        IrisSessionRegistry current = registry;
        if (current == null) {
            return;
        }
        String sessionId = event.getPlayer().getUniqueId().toString();
        current.unregister(sessionId);
        IrisCursorRequestService cursor = cursorService;
        if (cursor != null) {
            cursor.clearSession(sessionId);
        }
        IrisVisionRequestService vision = visionService;
        if (vision != null) {
            vision.clearSession(sessionId);
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        IrisProtocolServer current = protocolServer;
        if (current == null || !IrisProtocol.CHANNEL.equals(channel)) {
            return;
        }
        current.onClientFrame(player.getUniqueId().toString(), message);
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
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        J.runEntity(player, () -> deliver(player, frame));
    }

    private void deliver(Player player, byte[] frame) {
        if (!player.isOnline() || !player.getListeningPluginChannels().contains(IrisProtocol.CHANNEL)) {
            return;
        }
        player.sendPluginMessage(Iris.instance, IrisProtocol.CHANNEL, frame);
    }

    private void syncDimension(Player player, int attemptsLeft) {
        IrisProtocolServer protocol = protocolServer;
        IrisSessionRegistry current = registry;
        if (protocol == null || current == null || !player.isOnline()) {
            return;
        }
        String sessionId = player.getUniqueId().toString();
        IrisSession session = current.get(sessionId);
        if (session == null) {
            return;
        }
        if (!session.isReady()) {
            if (attemptsLeft > 1) {
                J.runEntity(player, () -> syncDimension(player, attemptsLeft - 1), DIMENSION_SYNC_RETRY_TICKS);
            }
            return;
        }
        World world = player.getWorld();
        Engine engine = engineFor(world);
        if (engine != null) {
            protocol.sendDimensionStatus(sessionId, engine.getDimension().getLoadKey(), engine.getPackSource().getFileName().toString(),
                    world.getSeed(), engine.getMinHeight(), engine.getMaxHeight(), true);
            return;
        }
        protocol.sendDimensionStatus(sessionId, WorldIdentity.serialize(world), "", world.getSeed(), world.getMinHeight(), world.getMaxHeight(), false);
    }

    private static Engine resolveEngine(String sessionId) {
        UUID playerId = parseUuid(sessionId);
        if (playerId == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return null;
        }
        return engineFor(player.getWorld());
    }

    private static Engine engineFor(World world) {
        PlatformChunkGenerator access = IrisToolbelt.access(world);
        if (access == null) {
            return null;
        }
        Engine engine = access.getEngine();
        if (engine == null || engine.isClosed()) {
            return null;
        }
        return engine;
    }

    private static UUID parseUuid(String sessionId) {
        try {
            return UUID.fromString(sessionId);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String brand() {
        return "Iris " + Iris.instance.getDescription().getVersion() + " (Bukkit)";
    }
}
