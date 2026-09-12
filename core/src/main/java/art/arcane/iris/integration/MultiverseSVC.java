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

package art.arcane.iris.integration;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

/**
 * Brings Iris' Multiverse integration up exactly when Multiverse is usable and takes it back down when it
 * is not.
 * <p>
 * Nothing in this class names a Multiverse type. The guard listener does, and Bukkit resolves a listener's
 * handler parameter types at registration, so it is only constructed once Multiverse is enabled. Iris
 * declares {@code loadbefore: Multiverse-Core}, which means Multiverse normally enables after Iris and the
 * plugin event is the live path; a reload or a plugin manager can invert that, so enablement is checked
 * directly as well.
 */
public class MultiverseSVC implements IrisService {
    private Listener guard;

    @Override
    public void onEnable() {
        activate();
    }

    @Override
    public void onDisable() {
        deactivate();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (!MultiverseCoreLink.MULTIVERSE_PLUGIN.equals(event.getPlugin().getName())) {
            return;
        }
        activate();
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (!MultiverseCoreLink.MULTIVERSE_PLUGIN.equals(event.getPlugin().getName())) {
            return;
        }
        deactivate();
    }

    /**
     * Registers the destructive-command guard and re-imposes Iris' intended Multiverse state on every
     * world Iris owns. Multiverse imports the worlds it did not know about during its own enable, which is
     * before this runs, so the reconciliation pass is the only thing that can correct those entries.
     */
    private synchronized void activate() {
        if (guard != null) {
            return;
        }
        MultiverseCoreLink link = IrisServices.getOrNull(MultiverseCoreLink.class);
        if (link == null || !link.isActive()) {
            return;
        }
        try {
            Listener listener = new MultiverseGuardListener(link);
            BukkitPlatform.volmitPlugin().registerListener(listener);
            guard = listener;
        } catch (Throwable failure) {
            // A Multiverse whose world events Iris cannot bind to is a Multiverse Iris must not touch.
            IrisLogging.warn("Multiverse world events are not bindable (%s); Iris will not guard its"
                    + " destructive commands.", failure.getClass().getSimpleName());
            return;
        }
        link.reconcileOwnedWorlds();
    }

    private synchronized void deactivate() {
        Listener listener = guard;
        if (listener == null) {
            return;
        }
        guard = null;
        try {
            BukkitPlatform.volmitPlugin().unregisterListener(listener);
        } catch (Throwable alreadyUnregistered) {
            // Shutdown ordering already unregisters every handler.
            IrisLogging.debug("Multiverse guard listener was already unregistered: "
                    + alreadyUnregistered.getClass().getName());
        }
    }
}
