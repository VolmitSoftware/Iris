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

package art.arcane.iris.world;

import art.arcane.iris.Iris;
import art.arcane.iris.localization.C;
import art.arcane.iris.world.safeguard.GenerationRefusalNotice;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Reports Iris worlds that refused to generate: a console banner that says what the refusal does to the server, and
 * an operator notice on join for as long as the world stays unloaded, since the banner scrolls away.
 */
public final class WorldRefusalReporter implements Listener {
    private static final String UNRESOLVED_KEY = "unresolved key";

    private final Map<String, String> operatorNotices = new ConcurrentSkipListMap<>();
    private volatile boolean startupWorldsCreated;

    /**
     * Iris enabled on a running server (a hotload) finds the startup worlds already loaded.
     */
    public void attach(Plugin plugin) {
        if (!Bukkit.getWorlds().isEmpty()) {
            startupWorldsCreated = true;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Paper creates every startup level before it prepares any, and preparing one fires the first WorldLoadEvent.
     * A generator asked for after that belongs to a runtime create, which fails only its caller.
     */
    public boolean startupWorldsCreated() {
        return startupWorldsCreated;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        startupWorldsCreated = true;
        try {
            operatorNotices.remove(WorldIdentity.key(event.getWorld()).toString());
        } catch (RuntimeException unkeyed) {
            Iris.debug("Loaded world " + event.getWorld().getName() + " has no key to clear a refusal notice by");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (operatorNotices.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isOp() && !player.hasPermission("iris.all")) {
            return;
        }
        for (String notice : operatorNotices.values()) {
            player.sendMessage(C.RED + notice);
        }
    }

    /**
     * Never throws: the caller is handing CraftServer a fail-closed generator or failing a load, and a reporting
     * failure must not turn either into something else.
     */
    public void report(String worldName, @Nullable NamespacedKey worldKey, Throwable failure) {
        String key = worldKey == null ? UNRESOLVED_KEY : worldKey.toString();
        try {
            for (String line : notice(worldName, key, failure, startupWorldsCreated)) {
                Iris.error(line);
            }
            operatorNotices.put(worldKey == null ? worldName : key, "Iris world '" + worldName + "' (" + key
                    + ") did not load and refuses to generate: " + GenerationRefusalNotice.summary(failure));
        } catch (Throwable reportingFailure) {
            System.err.println("[Iris] Iris refused to generate world '" + worldName + "' (" + key
                    + "); the refusal could not be reported: " + reportingFailure.getClass().getName());
        }
    }

    static List<String> notice(String worldName, String worldKey, Throwable failure, boolean startupWorldsCreated) {
        return GenerationRefusalNotice.compose(
                "Iris refused to generate world '" + worldName + "' (" + worldKey + ")",
                GenerationRefusalNotice.causes(failure),
                List.of(
                        "Iris does not generate this world and does not let vanilla or any other generator"
                                + " write it; no chunks are written.",
                        startupWorldsCreated
                                ? "The world does not load; the server keeps running. Fix the cause, then load it again."
                                : "Server startup stops before this world loads. Fix the cause, then start the server again."
                ));
    }
}
