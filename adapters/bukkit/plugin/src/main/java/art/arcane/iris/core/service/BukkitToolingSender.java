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

package art.arcane.iris.core.service;

import art.arcane.iris.core.project.IrisProject;
import art.arcane.iris.core.tooling.IrisServedPack;
import art.arcane.iris.core.tooling.SenderWorld;
import art.arcane.iris.core.tooling.ServedPack;
import art.arcane.iris.core.tooling.ToolingSender;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * A Bukkit sender of a tooling command. It reads the world the sender stands in and the open studio when it is built, which
 * the adapter does on the thread that received the command, and delivers messages on the sender's own thread.
 */
final class BukkitToolingSender implements ToolingSender {
    private final CommandSender sender;
    private final VolmitSender messages;
    private final Optional<SenderWorld> world;
    private final Optional<ServedPack> openStudioPack;

    BukkitToolingSender(CommandSender sender) {
        this.sender = sender;
        this.messages = new VolmitSender(sender);
        this.world = standingWorld(sender);
        this.openStudioPack = openStudio();
    }

    @Override
    public String name() {
        return sender.getName();
    }

    @Override
    public void info(String message) {
        deliver(C.WHITE + message);
    }

    @Override
    public void success(String message) {
        deliver(C.GREEN + message);
    }

    @Override
    public void error(String message) {
        deliver(C.RED + message);
    }

    @Override
    public <T> Optional<T> extension(Class<T> type) {
        return type.isInstance(sender) ? Optional.of(type.cast(sender)) : Optional.empty();
    }

    @Override
    public boolean isOperator() {
        return sender.hasPermission(CommandSVC.ROOT_PERMISSION);
    }

    @Override
    public Optional<SenderWorld> world() {
        return world;
    }

    @Override
    public Optional<ServedPack> openStudioPack() {
        return openStudioPack;
    }

    private void deliver(String message) {
        if (sender instanceof Player player) {
            J.runEntity(player, () -> messages.sendMessage(message));
            return;
        }
        messages.sendMessage(message);
    }

    private static Optional<SenderWorld> standingWorld(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return Optional.empty();
        }
        return Optional.ofNullable(IrisToolbelt.access(player.getWorld()))
                .map(PlatformChunkGenerator::getEngine)
                .filter(engine -> !engine.isClosed())
                .map(SenderWorld::of);
    }

    private static Optional<ServedPack> openStudio() {
        StudioSVC studio = IrisServices.getOrNull(StudioSVC.class);
        if (studio == null || !studio.isProjectOpen()) {
            return Optional.empty();
        }
        return Optional.ofNullable(studio.getActiveProject())
                .map(IrisProject::getActiveProvider)
                .map(PlatformChunkGenerator::getEngine)
                .filter(engine -> !engine.isClosed())
                .map(Engine::getData)
                .map(IrisServedPack::new);
    }
}
