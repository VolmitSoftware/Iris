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

package art.arcane.iris.core.service;

import art.arcane.iris.Iris;
import art.arcane.iris.core.commands.CommandIris;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.util.decree.DecreeContext;
import art.arcane.iris.util.decree.DecreeSystem;
import art.arcane.iris.util.decree.virtual.VirtualDecreeCommand;
import art.arcane.iris.util.format.C;
import art.arcane.iris.util.plugin.IrisService;
import art.arcane.iris.util.plugin.VolmitSender;
import art.arcane.iris.util.scheduling.J;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class CommandSVC implements IrisService, DecreeSystem {
    private final KMap<String, CompletableFuture<String>> futures = new KMap<>();
    private final transient AtomicCache<VirtualDecreeCommand> commandCache = new AtomicCache<>();
    private CompletableFuture<String> consoleFuture = null;

    @Override
    public void onEnable() {
        Iris.instance.getCommand("iris").setExecutor(this);
        J.a(() -> {
            DecreeContext.touch(Iris.getSender());
            try {
                getRoot().cacheAll();
            } finally {
                DecreeContext.remove();
            }
        });
    }

    @Override
    public void onDisable() {

    }

    @EventHandler
    public void on(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().startsWith("/") ? e.getMessage().substring(1) : e.getMessage();

        if (msg.startsWith("irisdecree ")) {
            String[] args = msg.split("\\Q \\E");
            CompletableFuture<String> future = futures.get(args[1]);

            if (future != null) {
                future.complete(args[2]);
                e.setCancelled(true);
                return;
            }
        }

        if ((msg.startsWith("locate ") || msg.startsWith("locatebiome ")) && IrisToolbelt.isIrisWorld(e.getPlayer().getWorld())) {
            new VolmitSender(e.getPlayer()).sendMessage(C.RED + "Locating biomes & objects is disabled in Iris Worlds. Use /iris studio goto <biome>");
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void on(ServerCommandEvent e) {
        if (consoleFuture != null && !consoleFuture.isCancelled() && !consoleFuture.isDone()) {
            if (!e.getCommand().contains(" ")) {
                String pick = e.getCommand().trim().toLowerCase(Locale.ROOT);
                consoleFuture.complete(pick);
                e.setCancelled(true);
            }
        }
    }

    @Override
    public VirtualDecreeCommand getRoot() {
        return commandCache.aquireNastyPrint(() -> VirtualDecreeCommand.createRoot(new CommandIris()));
    }

    public void post(String password, CompletableFuture<String> future) {
        futures.put(password, future);
    }

    public void postConsole(CompletableFuture<String> future) {
        consoleFuture = future;
    }
}
